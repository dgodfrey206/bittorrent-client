/* String of encoded data passed into read function */
/* read function parses all tokens, returns BObject[] */

package util.bencode;

import util.bencode.BList;
import util.bencode.BDict;
import util.bencode.BNumber;
import util.bencode.BString;

import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public class BDecoder {
    /* read:  parses an encoded string into Bobjects */
    public static BObject[] read(final String str) {
        AtomicInteger pos = new AtomicInteger(0);
        List<BObject> tokens = new ArrayList<BObject>();

        while (pos.get() < str.length()) {
            BObject obj = read(str, pos);
            if (obj == null) {
                break;
            }
            tokens.add(obj);
        }
        return tokens.toArray(new BObject[tokens.size()]);
    }

    /* read:  parse the next token of Bencoded str */
    /* 0-9 begins BString, i begins BNumber, l begins BList, d begins BDict */
    public static BObject read(final String str, final AtomicInteger pos) {
        char c;
        if (pos.get() < str.length()) {
            c = str.charAt(pos.get());
        } else {
            System.err.println("tried to read() past end of string");
            return null;
        }

        if (c >= '0' && c <= '9') {
            return BString.read(str, pos);
        } else if (c == 'i') {
            return BNumber.read(str, pos);
        } else if (c == 'l') {
            return BList.read(str, pos);
        } else if (c == 'd') {
            return BDict.read(str, pos);
        } else {
            throw new RuntimeException("\ngetToken: unrecognized type: " + c);
        }
    }
}
