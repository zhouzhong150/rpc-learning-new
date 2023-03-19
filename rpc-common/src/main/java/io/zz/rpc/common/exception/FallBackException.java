package io.zz.rpc.common.exception;
public class FallBackException extends RuntimeException{

    public FallBackException(String exception){
        super(exception);
    }

}
