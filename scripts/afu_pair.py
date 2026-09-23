#!/usr/bin/env python3
"""Tiny E2E peer for AfuRemote's P-256 pairing and HMAC request protocol."""
import base64, hashlib, hmac, json, os, sys, time, secrets
from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.kdf.hkdf import HKDF

def load(path):
    with open(path, encoding="utf-8") as f: return json.load(f)
def save(path, data):
    with open(path, "w", encoding="utf-8") as f: json.dump(data, f)
def main():
    cmd=sys.argv[1]
    if cmd=="start":
        _,_,device_id,name,path=sys.argv
        private=ec.generate_private_key(ec.SECP256R1())
        pub=private.public_key().public_bytes(serialization.Encoding.X962,serialization.PublicFormat.UncompressedPoint)
        der=private.public_key().public_bytes(serialization.Encoding.DER,serialization.PublicFormat.SubjectPublicKeyInfo)
        save(path,{"priv":base64.b64encode(private.private_bytes(serialization.Encoding.DER,serialization.PrivateFormat.PKCS8,serialization.NoEncryption())).decode(),"phone_pub":der.hex(),"device_id":device_id})
        print(json.dumps({"deviceId":device_id,"deviceName":name,"phonePub":base64.b64encode(der).decode()}))
    elif cmd=="finish":
        _,_,path,tv_b64=sys.argv; d=load(path); private=serialization.load_der_private_key(base64.b64decode(d["priv"]),None); tv_der=base64.b64decode(tv_b64)
        tv=serialization.load_der_public_key(tv_der); phone=bytes.fromhex(d["phone_pub"])
        tv_der=tv.public_bytes(serialization.Encoding.DER,serialization.PublicFormat.SubjectPublicKeyInfo)
        digest=hashlib.sha256(b"afuremote-pair-v1"+phone+tv_der+d["device_id"].encode()).digest()
        code=f"{int.from_bytes(digest[:4],'big')%1000000:06d}"
        secret=private.exchange(ec.ECDH(),tv)
        salt=hashlib.sha256(phone+tv_der).digest()
        key=HKDF(algorithm=hashes.SHA256(),length=32,salt=salt,info=b"afuremote-auth-v1"+d["device_id"].encode()).derive(secret)
        d["key"]=key.hex(); d["tv_pub"]=tv_b64; save(path,d); print(code[:3]+" "+code[3:])
    elif cmd=="sign":
        _,_,path,method,url_path,body=sys.argv; d=load(path); ts=str(int(time.time()*1000)); nonce=secrets.token_hex(16)
        body_hash=hashlib.sha256(body.encode()).hexdigest(); msg=f"{method}\n{url_path}\n{ts}\n{nonce}\n{body_hash}".encode()
        sig=hmac.new(bytes.fromhex(d["key"]),msg,hashlib.sha256).hexdigest()
        print("\n".join([d["device_id"],ts,nonce,sig]))
    else: raise SystemExit("usage: start|finish|sign")
if __name__=="__main__": main()
