package util.bencode;

import java.util.concurrent.atomic.AtomicInteger;

public class BNumber implements BObject {
    private int number;

    public BNumber(final int n) {
        this.number = n;
    }

    public int getNumber() {
        return number;
    }

    public void setNumber(final int n) {
        this.number = n;
    }

    @Override
    public String encode() {
        return "i" + number + "e";
    }

    /* read:  parse a BNumber object from a bencoded string */
    public static BNumber read(final String str, final AtomicInteger pos) {
        if (str.charAt(pos.get()) == 'i') {
            pos.getAndIncrement();
        }

        int value = 0;
        final int endPos = str.indexOf('e', pos.get());
        try {
            value = Integer.parseInt(str.substring(pos.get(), endPos));
        } catch (NumberFormatException ex) {
            System.err.println("error: misformatted BNumber object");
            return null;
        }
        pos.set(endPos + 1);

        return new BNumber(value);
    }

    /* print:  produce a human-readable string */
    @Override
    public String print() {
        return String.valueOf(number);
    }

    @Override
    public BObjectType getType() {
        return BObjectType.BNUMBER;
    }
}
