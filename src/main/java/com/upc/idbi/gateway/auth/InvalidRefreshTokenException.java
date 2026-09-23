package com.upc.idbi.gateway.auth;

/** El refresh token no existe, ya fue rotado/usado, o expiró — el cliente
 *  debe cerrar sesión y pedir credenciales de nuevo (ver GlobalExceptionHandler). */
public class InvalidRefreshTokenException extends RuntimeException {

    public InvalidRefreshTokenException(String message) {
        super(message);
    }
}
