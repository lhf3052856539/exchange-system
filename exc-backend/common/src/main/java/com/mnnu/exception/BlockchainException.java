package com.mnnu.exception;

/**
 * 区块链交互异常
 */


public class BlockchainException extends BusinessException {

    public BlockchainException(String message) {
        super(503, message);
    }

    public BlockchainException(String message, Throwable cause) {
        super(503, message);
        this.initCause(cause);
    }
}
