package util.lib;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.Formatter;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

public class BitLibrary {
    /* getRandomSet:  returns an array of n (or hi - lo, whichever is smaller) */
    /* random integers from the range [lo, hi) */
    public static Set<Integer> getRandomSet(int n, int lo, int hi) {
        Random random = new Random(System.currentTimeMillis());
        HashSet<Integer> s = new HashSet<Integer>();
        while (s.size() < n && s.size() < hi - lo) {
            s.add(random.nextInt(hi) + lo);
        }
        return s;
    }

    public static String bytesToHex(final byte[] hash) {
    	Formatter formatter = new Formatter();
    	for (byte b : hash) {
    	    formatter.format("%02x", b);
    	}
    	String result = formatter.toString();
    	formatter.close();
    	return result;
    }

    /* bitsToBoolean: convert byte[] bitfield to boolean[] bitfield */
    /* LEN is the number of valid entries (i.e., 8*bitfield.length - pad bits) */
    public static boolean[] bitsToBoolean(byte[] bitfield, int len) {
        boolean[] boolfield = new boolean[len];
        for (int i = 0; i < boolfield.length; ++i) {
            int bytePos = i / 8;
            int bitPos = i % 8;
            if ((bitfield[bytePos] & (128 >> bitPos)) > 0) {
                boolfield[i] = true;
            } else {
                boolfield[i] = false;
            }
        }
        return boolfield;
    }

    /* booleanToBits: convert boolean[] bitfield to byte[] bitfield */
    public static byte[] booleanToBits(boolean[] boolfield) {
        byte[] bitfield = new byte[(boolfield.length / 8) + 1];
        for (int i = 0; i < bitfield.length; ++i) {
            bitfield[i] = 0;
        }

        for (int i = 0; i < boolfield.length; ++i) {
            int bytePos = i / 8;
            int bitPos = i % 8;

            if (boolfield[i]) {
                bitfield[bytePos] |= (128 >> bitPos);
            }
        }

        return bitfield;
    }

    /* isAllTrue:  returns true if boolean array is all true values */
    /* e.g., true if array is a bitfield and the download is complete */
    public static boolean isAllTrue(boolean[] array)
    {
        for (boolean val : array) {
            if (val == false) {
                return false;
            }
        }
        return true;
    }

    /* hasStr:  returns true iff array of strings contains an instance of str */
    public static boolean hasStr(String[] array, String str) {
        for (String s : array) {
            if (s.equals(str)) {
                return true;
            }
        }
        return false;
    }

    /* getBitString: return a reader-friendly string of a bitfield */
    public static String getBitString(boolean[] bitfield) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        for (int i = 0; i < bitfield.length - 1; ++i) {
            if (bitfield[i]) {
                sb.append("1, ");
            } else {
                sb.append("0, ");
            }
        }
        if (bitfield[bitfield.length - 1]) {
            sb.append("1}");
        } else {
            sb.append("0}");
        }
        return sb.toString();
    }

    /* getTimeString: return a reader-friendly timestamp to print to the log */
    public static String getTimeString() {
        return new SimpleDateFormat("hh:mm:ss").format(new Date());
    }

    /* getSHA1:  returns the 20-byte SHA1 hash of the stringToHash */
    public static byte[] getSHA1(final String stringToHash) {
        MessageDigest md = null;
        try {
            md = MessageDigest.getInstance("SHA-1");
            md.reset();
            byte[] hashData = stringToHash.getBytes("UTF-8");
            md.update(hashData);
        } catch (NoSuchAlgorithmException|UnsupportedEncodingException ex) {
            ex.printStackTrace();
        }
        if (md == null) {
            return null;
        }
        return md.digest();
    }

    /* writeByteBuffer:  writes a byte buffer out to the output stream */
    public static void writeByteBuffer(ByteBuffer buf, OutputStream out) {
        WritableByteChannel channel = Channels.newChannel(out);
        try {
            channel.write(buf);
        } catch (IOException ex) {

        }
    }
}
