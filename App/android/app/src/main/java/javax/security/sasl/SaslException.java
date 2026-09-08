package javax.security.sasl;

import java.io.IOException;

public class SaslException extends IOException {
    public SaslException() {
        super();
    }

    public SaslException(String detail) {
        super(detail);
    }

    public SaslException(String detail, Throwable ex) {
        super(detail, ex);
    }
}
