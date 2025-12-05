import logging
import random
import re
import socket
import string
import sys
import threading
import time
import uuid 
from os import environ
from decimal import Decimal
from typing import Union, List, Dict

class QuitCommandException(Exception):
    """Custom exception to break out of the command loop."""
    pass

try:
    import boto3
    from boto3.dynamodb.conditions import Key
    from botocore.exceptions import ClientError
except ImportError:
    print("ERROR: boto3 library not found. Please run 'pip install boto3'")
    sys.exit(-1)

LOG_LEVEL = int(environ.get("PIRC_LOG_LEVEL", logging.INFO))
logging.basicConfig(level=LOG_LEVEL, format='%(asctime)s - %(levelname)s - %(threadName)s - %(message)s')
log = logging.getLogger("pirc_dynamodb")

command_pattern = r"^(?P<source>:[a-zA-Z0-9@#*_.+!\[\]{}\\|\-]+ )?(?P<command>([A-Z]+)|motd)(?P<subcommands>( [a-zA-Z0-9@#*_.+!\[\]{}\\|\-]+)*?)( :(?P<content>.*))?$"

class Command:
    def __init__(self, text: str) -> None:
        match_obj = re.match(command_pattern, text.strip())
        if not match_obj: raise SyntaxError("Invalid message received!")
        source = match_obj["source"]
        self.source = source[1:-1] if source else None
        self.command = match_obj["command"]
        self.subcommands = match_obj["subcommands"].strip().split(" ") if match_obj["subcommands"] else None
        self.content = match_obj["content"]

def compute_id(nick: str, user: str, host: str) -> str:
    return f"{nick}!{user}@{host}"

class ClientRegistration:
    def __init__(self, client: socket.socket, host: str, port: int) -> None:
        self.client = client
        self.nick = f"guest{random.randint(1000,9999)}"
        self.user = "guest"
        self.host = f"{host}:{port}"
        self.channels = [] 
        self.registered = False
        self.nick_set = False
        self.user_set = False

    def id(self) -> str:
        return compute_id(self.nick, self.user, self.host)
    
class DynamoDBService:
    def __init__(self, region='us-east-1', server_id='server_local'):
        self.dynamodb = boto3.resource('dynamodb', region_name=region)
        self.server_id = server_id
        
        self.tbl_users_name = "PircUsers"
        self.tbl_channels_name = "PircChannels"
        self.tbl_msgs_name = "PircMessages"
        
        self._ensure_table(self.tbl_users_name, "Nick")
        self._ensure_table(self.tbl_channels_name, "Name")
        self._ensure_table(self.tbl_msgs_name, "Target", "Timestamp")
        
        self.users_table = self.dynamodb.Table(self.tbl_users_name)
        self.channels_table = self.dynamodb.Table(self.tbl_channels_name)
        self.msgs_table = self.dynamodb.Table(self.tbl_msgs_name)

    def _ensure_table(self, table_name, pk, sk=None):
        try:
            self.dynamodb.Table(table_name).load()
            log.info(f"Table {table_name} found.")
        except ClientError as e:
            if e.response['Error']['Code'] == 'ResourceNotFoundException':
                log.info(f"Creating table {table_name}...")
                key_schema = [{'AttributeName': pk, 'KeyType': 'HASH'}]
                attr_defs = [{'AttributeName': pk, 'AttributeType': 'S'}]
                
                if sk:
                    key_schema.append({'AttributeName': sk, 'KeyType': 'RANGE'})
                    attr_defs.append({'AttributeName': sk, 'AttributeType': 'N'})

                self.dynamodb.create_table(
                    TableName=table_name,
                    KeySchema=key_schema,
                    AttributeDefinitions=attr_defs,
                    BillingMode='PAY_PER_REQUEST'
                ).wait_until_exists()
                log.info(f"Table {table_name} created successfully.")

    def is_nick_online(self, nick: str) -> bool:
        """Checks if a nick is currently marked as ONLINE in DynamoDB."""
        try:
            response = self.users_table.get_item(
                Key={'Nick': nick},
                ProjectionExpression='#s',
                ExpressionAttributeNames={'#s': 'Status'}
            )
            item = response.get('Item')
            
            if item and item.get('Status') == 'ONLINE':
                log.info(f"DB-CHECK: Nick {nick} found and is ONLINE.")
                return True
            return False
        except ClientError as e:
            log.error(f"Error checking nick status in DB: {e}")
            return True

    def register_user(self, client: ClientRegistration):
        self.users_table.put_item(Item={
            'Nick': client.nick,
            'User': client.user,
            'Host': client.host,
            'ServerID': self.server_id,
            'Status': 'ONLINE',
            'LastSeen': Decimal(time.time())
        })

    def quit_user(self, client: ClientRegistration):
        try:
            if client.nick.startswith("guest") and not client.nick_set:
                return
            self.users_table.update_item(
                Key={'Nick': client.nick},
                UpdateExpression="set #s = :s",
                ExpressionAttributeNames={'#s': 'Status'},
                ExpressionAttributeValues={':s': 'OFFLINE'}
            )
        except ClientError:
            pass

    def join_channel(self, client: ClientRegistration, channel: str):
        try:
            self.channels_table.update_item(
                Key={'Name': channel},
                UpdateExpression="ADD Members :m SET Topic = if_not_exists(Topic, :t)",
                ExpressionAttributeValues={
                    ':m': {client.nick},
                    ':t': f"Welcome to {channel}"
                }
            )
        except ClientError as e:
            log.error(f"Error joining DB channel: {e}")

        resp = self.channels_table.get_item(Key={'Name': channel})
        return resp.get('Item', {}).get('Topic', 'No Topic')

    def part_channel(self, client: ClientRegistration, channel: str):
        try:
            self.channels_table.update_item(
                Key={'Name': channel},
                UpdateExpression="DELETE Members :m",
                ExpressionAttributeValues={':m': {client.nick}}
            )
        except ClientError:
            pass

    def get_all_channels(self) -> List[Dict]:
        """
        Scans the Channels table to retrieve all channel names, topics, 
        and calculates the current number of members.
        """
        try:
            response = self.channels_table.scan(
                ProjectionExpression="#n, Topic, Members",
                ExpressionAttributeNames={'#n': 'Name'}
            )
            
            channels_data = []
            for item in response.get('Items', []):
                member_count = len(item.get('Members', set()))
                
                channels_data.append({
                    'name': item['Name'],
                    'topic': item.get('Topic', ''),
                    'members': member_count
                })
            
            return channels_data
        
        except ClientError as e:
            log.error(f"Error scanning channels table for LIST: {e}")
            return []

    def send_message(self, target: str, sender_nick: str, content: str):
        timestamp = Decimal(time.time())
        self.msgs_table.put_item(Item={
            'Target': target,
            'Timestamp': timestamp,
            'Sender': sender_nick,
            'Content': content,
            'SourceServer': self.server_id
        })       
        log.debug(f"DB-OUT: {sender_nick} -> {target}")

    def fetch_new_messages(self, target: str, last_check: Decimal) -> List[Dict]:
        try:
            response = self.msgs_table.query(
                KeyConditionExpression=Key('Target').eq(target) & Key('Timestamp').gt(last_check)
            )
            return response.get('Items', [])
        except ClientError as e:
            log.error(f"Polling error for {target}: {e}")
            return []

class MessagePoller(threading.Thread):
    def __init__(self, db: DynamoDBService, server, interval=1.0):
        super().__init__()
        self.db = db
        self.server = server
        self.interval = interval
        self.running = True
        self.last_check = Decimal(time.time())

    def run(self):
        log.info("Started Background Message Poller")
        while self.running:
            poll_targets = set()
            
            with self.server.users_lock:
                for nick in self.server.users.keys():
                    poll_targets.add(nick)
                
                for client in self.server.clients.values():
                    for chan in client.channels:
                        poll_targets.add(chan)
            
            current_time = Decimal(time.time())
            
            for target in poll_targets:
                messages = self.db.fetch_new_messages(target, self.last_check)
                for msg in messages:
                    if msg['SourceServer'] != self.db.server_id:
                        self.server.broadcast_remote_message(
                            target=msg['Target'],
                            sender=msg['Sender'],
                            content=msg['Content']
                        )
            
            self.last_check = current_time
            time.sleep(self.interval)

    def stop(self):
        self.running = False

class PircServer:
    def __init__(self, host="0.0.0.0", port=6667, db_region='us-east-1'):
        self.host = host
        self.port = port
        self.server_name = "pirc-ec2"
        self.server_id = str(uuid.uuid4())[:8]
        
        self.clients = {} 
        self.users = {}   
        self.users_lock = threading.Lock()
        
        self.db = DynamoDBService(region=db_region, server_id=self.server_id)
        self.poller = MessagePoller(self.db, self)

    def run(self) -> None:
        self.server_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self.server_socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        self.server_socket.bind((self.host, self.port))
        self.server_socket.listen(5)
        
        log.info(f"Server {self.server_id} listening on {self.host}:{self.port}")
        self.poller.start()

        try:
            while True:
                client_socket, address = self.server_socket.accept()
                t = threading.Thread(target=self._handle_client, args=(client_socket, address))
                t.daemon = True
                t.start()
        except KeyboardInterrupt:
            pass
        finally:
            self.poller.stop()
            self.server_socket.close()

    def _cleanup_client(self, client_data):
        with self.users_lock:
            if client_data.client.fileno() in self.clients:
                del self.clients[client_data.client.fileno()]
            if client_data.nick in self.users:
                del self.users[client_data.nick]
        
        self.db.quit_user(client_data)
        for chan in client_data.channels:
            self.db.part_channel(client_data, chan)
        
        try:
            client_data.client.close()
        except: pass

    def send(self, client, msg):
        try:
            client.sendall(f"{msg}\r\n".encode("utf-8"))
        except: pass

    def send_reply(self, client: ClientRegistration, numeric_code: str, text: str, target: str = None):
        """Sends a standard IRC reply (e.g., errors 4xx, 3xx) to a client."""
        target = target if target else client.nick
        
        msg = f":{self.server_name} {numeric_code} {target} {text}"
        self.send(client.client, msg)

    def broadcast_local(self, message: str, targets: List[ClientRegistration], exclude=None):
        for client in targets:
            if client != exclude:
                self.send(client.client, message)

    def broadcast_remote_message(self, target: str, sender: str, content: str):
        """Called by Poller when a message arrives from DynamoDB."""
        formatted_msg = f":{sender} PRIVMSG {target} :{content}"
        
        with self.users_lock:
            if target.startswith("#"):
                recipients = [
                    c for c in self.clients.values() 
                    if target in c.channels and c.registered
                ]
                self.broadcast_local(formatted_msg, recipients)
            
            else:
                if target in self.users:
                    client = self.users[target]
                    self.send(client.client, formatted_msg)
                    log.info(f"Delivered remote PM from {sender} to local user {target}")

    def _handle_client(self, sock, addr):
        client = ClientRegistration(sock, addr[0], addr[1])

        with self.users_lock:
            self.clients[sock.fileno()] = client
        
        try:
            while True:
                data = sock.recv(4096)
                if not data: break
                lines = data.decode("utf-8", errors='ignore').split("\r\n")
                for line in lines:
                    if not line: continue
                    log.debug(f"Received command from {client.nick}: {line}")
                    try:
                        self.handle_command(client, Command(line))
                    except QuitCommandException:
                        return
                    except Exception as e:
                        log.error(f"Cmd Error: {e}")
        finally:
            self._cleanup_client(client)

    def handle_command(self, client: ClientRegistration, cmd: Command):
        if cmd.command == "NICK":
            new_nick = cmd.subcommands[0]
            if self.db.is_nick_online(new_nick):
                self.send_reply(client, "433", f"{new_nick} already exists and is ONLINE")
                return
            client.nick = new_nick
            client.nick_set = True
            with self.users_lock:
                self.users[new_nick] = client
            self.check_reg(client)

        elif cmd.command == "USER":
            client.user = cmd.subcommands[0]
            client.user_set = True
            self.check_reg(client)
            self.send_reply(client, "001",f":Welcome, {client.id()}")
            self.send_reply(client, "002", f":Your host is {self.server_name}, running version 1.0")
            self.send_reply(client, "003", f":This server was created on 02/12/2025.")
            self.send_reply(client, "004", f":{self.server_name} 1.0  ")
            self.send_reply(client, "005",f":NETWORK=PIRC is supported by this server")

        elif cmd.command == "JOIN":
            channel = cmd.subcommands[0]
            if channel not in client.channels:
                topic = self.db.join_channel(client, channel)
                client.channels.append(channel)
                self.send(client.client,f":{client.id().split(':')[0]} JOIN {channel}")
                self.send_reply(client,"332",f"{channel} :{topic}")
                self.send_reply(client,"353",f"= {channel} :@{client.nick}")
                self.send(client.client, f":{self.server_name} 366 {client.nick} {channel} :End of /NAMES list")

        elif cmd.command == "PRIVMSG":
            if not cmd.subcommands: return
            target = cmd.subcommands[0]
            msg = cmd.content
            
            self.db.send_message(target, client.nick, msg)
            
            with self.users_lock:
                if target.startswith("#"):
                    local_recipients = [
                        c for c in self.clients.values() 
                        if target in c.channels and c.registered
                    ]
                    fmt_msg = f":{client.id()} PRIVMSG {target} :{msg}"
                    self.broadcast_local(fmt_msg, local_recipients, exclude=client)
                
                else:
                    if target in self.users:
                        recipient = self.users[target]
                        fmt_msg = f":{client.id()} PRIVMSG {target} :{msg}"
                        self.send(recipient.client, fmt_msg)
                    else:
                        pass
        elif cmd.command == "PART":
            if not cmd.subcommands:
                self.send_reply(client, "461", "PART :Not enough parameters", "PART")
                return
            target = cmd.subcommands[0]
            msg = "PART MESSAGE - " + cmd.content
            channel_name = cmd.subcommands[0]
            part_message = cmd.content if cmd.content else client.nick

            if channel_name not in client.channels:
                self.send_reply(client, "442", f"{channel_name} :You're not on that channel", channel_name)
                return

            part_msg = f":{client.id().split(':')[0]} PART {channel_name} :{part_message}"
            
            with self.users_lock:
                recipients = [
                    c for c in self.clients.values() 
                    if channel_name in c.channels and c.registered
                ]
            
            self.broadcast_local(part_msg, recipients)
            self.db.send_message(target, client.nick, msg)
            client.channels.remove(channel_name)
            self.db.part_channel(client, channel_name)

        elif cmd.command == "LIST":
            if not client.registered:
                self.send_reply(client, "451", ":You have not registered", "LIST")
                return

            self.send_reply(client, "321", "Channel :Users Name", target="Channel")

            channels = self.db.get_all_channels()

            for channel in channels:
                channel_name = channel['name']
                member_count = channel['members']
                topic = channel['topic']
                
                self.send_reply(client, "322", f"{channel_name} {member_count} :{topic}", target=channel_name)

            self.send_reply(client, "323", ":End of /LIST")
        elif cmd.command == "PING":
            self.send(client.client, f"PONG {cmd.subcommands[0]}")
            
        elif cmd.command == "QUIT":
            self._cleanup_client(client)
            raise QuitCommandException

    def check_reg(self, client):
        if client.nick_set and client.user_set and not client.registered:
            client.registered = True
            self.db.register_user(client)
            self.send(client.client, f"001 {client.nick} :Welcome to Distributed PIRC {client.nick}")

if __name__ == "__main__":
    port = 6667
    if len(sys.argv) > 1 and ":" in sys.argv[1]:
        port = int(sys.argv[1].split(":")[1])
    
    server = PircServer(port=port, db_region='us-east-1')
    server.run()