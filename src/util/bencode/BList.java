package util.bencode;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/* BList:  handles Bencoding for list of BObjects */
public class BList extends ArrayList<BObject> implements BObject {
    /* encode:  turn each object in list into string repr. */
    @Override
    public String encode() {
        final StringBuilder buf = new StringBuilder();
        buf.append('l');

        for (final BObject obj : this) {
            buf.append(obj.encode());
        }
        buf.append('e');

        return buf.toString();
    }

    /* read:  decodes a list of objects */
    public static BList read(final String str, final AtomicInteger pos) {
        final BList tokens = new BList();

        if (str.charAt(pos.get()) == 'l') {
            pos.incrementAndGet();
        }

        /* pass up to parent decoder to get each token */
        while (str.charAt(pos.get()) != 'e') {
            tokens.add(BDecoder.read(str, pos));
        }
        pos.incrementAndGet();

        return tokens;
    }

    /* print:  produce a human-readable string */
    @Override
    public String print() {
        final StringBuilder buf = new StringBuilder();
        buf.append("List:\n");
        for (final BObject obj : this) {
            buf.append(obj.print() + "\n");
        }

        return buf.toString();
    }

    @Override
    public BObjectType getType() {
        return BObjectType.BLIST;
    }
}
