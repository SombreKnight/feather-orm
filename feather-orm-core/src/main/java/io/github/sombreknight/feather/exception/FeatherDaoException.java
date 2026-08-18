package io.github.sombreknight.feather.exception;

/**
 * Feather ORM 统一运行时异常
 *
 * @author sombreknight
 */
public class FeatherDaoException extends RuntimeException {

    public FeatherDaoException() {
        super();
    }

    public FeatherDaoException(String message) {
        super(message);
    }

    public FeatherDaoException(Throwable cause) {
        super(cause);
    }

    public FeatherDaoException(String message, Throwable cause) {
        super(message, cause);
    }
}
