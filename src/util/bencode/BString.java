package util.bencode;

import java.util.concurrent.atomic.AtomicInteger;

/* BString:  BObject for a Bencoded string */
public class BString implements BObject {
    public String str;

    public BString(final String str) {
        this.str = str;
    }

    public String getString() {
        return str;
    }

    public void setString(final String str) {
        this.str = str;
    }

    @Override
    public String encode() {
        return str.length() + ":" + str;
    }

    /* read:  parse a BString object from a Bencoded string */
    public static BString read(final String str, AtomicInteger pos) {
        final int delimPos = str.indexOf(':', pos.get());
        final int len = Integer.parseInt(str.substring(pos.get(), delimPos));
        pos.set(delimPos + 1);

        final String token = str.substring(pos.get(), pos.get() + len);
        pos.set(pos.get() + len);

        return new BString(token);
    }

    /* print:  produce a human-readable string */
    @Override
    public String print() {
        return str;
    }

    @Override
    public BObjectType getType() {
        return BObjectType.BSTRING;
    }
}
