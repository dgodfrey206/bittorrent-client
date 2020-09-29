package util.bencode;

import util.bencode.BObject;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Map;
import java.util.HashMap;


/* BDict:  BObject for Bencode dictionary */
public class BDict extends HashMap<String, BObject> implements BObject {

    /* encode:  turn BDict into string representation */
    @Override
    public String encode() {
        final StringBuilder buf = new StringBuilder();
        buf.append('d');

        for (final Map.Entry<String, BObject> elt : entrySet()) {
            buf.append(elt.getKey().length() + ":" + elt.getKey()
                       + elt.getValue().encode());
        }
        buf.append('e');

        return buf.toString();
    }

    /* read:  decodes a BDict from a string */
    public static BDict read(final String str, final AtomicInteger pos) {
        final BDict dict = new BDict();
        if (str.charAt(pos.get()) == 'd') {
            pos.getAndIncrement();
        }

        while (str.charAt(pos.get()) != 'e') {
            final String key = BString.read(str, pos).getString();
            final BObject value = BDecoder.read(str, pos);
            dict.put(key, value);
        }
        pos.getAndIncrement();

        return dict;
    }

    /* print:  produce a human-readable string */
    @Override
    public String print() {
        final StringBuilder buf = new StringBuilder();
        buf.append("Dictionary:\n");
        for (final Map.Entry<String, BObject> elt : entrySet()) {
            buf.append(elt.getKey() + 
                       " -> " + elt.getValue().print() + "\n");
        }

        return buf.toString();
    }

    @Override
    public BObjectType getType() {
        return BObjectType.BDICT;
    }
}
