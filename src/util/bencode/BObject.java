package util.bencode;

public interface BObject {
    public enum BObjectType {
        BDICT,
        BSTRING,
        BNUMBER,
        BLIST
    }
    /* encode:  convert BObject to Bencoded string representation */
    public String encode();

    /* print:  produce a human-readable string */
    public String print();

    /* getType:  returns type of BObject */
    public BObjectType getType();
}
