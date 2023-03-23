package io.zz.rpc.protocol.header;

import io.zz.rpc.common.id.IdFactory;
import io.zz.rpc.constants.RpcConstants;

public class RpcHeaderFactory {

    public static RpcHeader getRequestHeader(String serializationType, int messageType){
        RpcHeader header = new RpcHeader();
        long requestId = IdFactory.getId();
        header.setMagic(RpcConstants.MAGIC);
        header.setRequestId(requestId);
        header.setMsgType((byte) messageType);
        header.setStatus((byte) 0x1);
        header.setSerializationType(serializationType);
        return header;
    }
}