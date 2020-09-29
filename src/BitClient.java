import java.nio.file.*;
import java.io.File;
import java.io.RandomAccessFile;
import java.io.IOException;
import java.io.FileOutputStream;
import java.io.BufferedOutputStream;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Random;
import java.util.Set;
import java.net.Socket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;

import util.bencode.*;        // interface for Bencoded objects
import util.lib.BitLibrary;   // various library functions for BitTorrent

/* BitClient:  manages a BitTorrent connection session */
public class BitClient {
    private static final String TRNT_DIR = "./test/torrents/";
    private static final String DNLD_DIR = "./test/downloads/";
    private static final String UPLD_DIR = "./test/uploads/";
    private static final int MAX_UNCHOKED = 4;         // only unchoke 4 at once
    private static final int SHA_LENGTH = 20;          // bytes in a SHA1 hash
    private static final int INT_LEN = 4;              // bytes in an Integer
    private static boolean _DEBUG = false;             // debugging flag
    private static String encoded;                     // Bencoded .torrent file
    private static String infoBencoded;                // Bencoded info dict
    private static int fileLength = -1;                // len of whole file
    private static int pieceLength = -1;               // len of each piece
    private static int numPieces = -1;                 // num. of pieces in file
    private static Random random = null;               // request random pieces
    private static boolean[] localBitfield = null;     // pieces client has
    private static String savePath = null;             // save location
    private static RandomAccessFile file = null;       // file to transfer
    private static String[] pieces = null;             // SHA1 of pieces
    private static String trackerURL = null;           // URL of tracker
    private static boolean isSeeder = false;           // client has entire file
    private static boolean runSlowly = false;          // run slowly for testing
    private static int welcomePort = 6789;             // port for listening
    private static BitWelcomer welcomer = null;        // welcomes new peers
    private static LinkedList<Socket> welcomeQ = null; // pending peer conn's
    private static ArrayList<BitPeer> peerList = null; // connected peers
    private static int numUnchoked = -1;

    public static void main(String[] args) {
        ByteBuffer lenBuf = ByteBuffer.allocate(INT_LEN);
        BitMessage unchoke = new BitMessage(BitMessage.MessageType.UNCHOKE);
        // get client settings from command line, including peerList
        if (parseArgs(args) == -1) {
            return;
        }

        // parse the metainfo from .torrent file
        if (initClient() == -1) {
            return;
        }
        // guaranteed initialized: fileLength, pieceLength, file, pieces,
        // welcomer, infoBencoded
        logOutput(BitLibrary.getTimeString() + ": PARSED .TORRENT INFO");
        logOutput("\t   LOCATION OF FILE " + savePath);
        logOutput("\t   FILE OF LENGTH " + fileLength);
        logOutput("\t   PCS. OF LENGTH " + pieceLength);
        logOutput("\t   INIT BITFIELD  " + BitLibrary.getBitString(localBitfield));
        logOutput(BitLibrary.getTimeString() 
                  + ": LISTENING ON PORT " + welcomePort);

        // open connection and send handshakes to all peers
        Iterator<BitPeer> it = peerList.iterator();
        while (it.hasNext()) {
            BitPeer peer = it.next();
            if (peer.connect() == -1) {
                it.remove();
                continue;
            }
            peer.sendHandshake(infoBencoded);
            BitMessage bfmsg = new BitMessage(BitMessage.MessageType.BITFIELD,
                                       BitLibrary.booleanToBits(localBitfield));
            sendMessage(peer, bfmsg);
            logOutput(BitLibrary.getTimeString() + ": HANDSHAKE INITIALIZED");
            peer.receiveHandshake(infoBencoded);
            logOutput(BitLibrary.getTimeString() + ": HANDSHAKE COMPLETE");
        }

        // randomly unchoke four peers (all peers if <= 4 are connected)
        if (peerList.size() <= MAX_UNCHOKED) {
            for (BitPeer peer : peerList) {
                sendMessage(peer, unchoke);
                peer.remoteIsChoked = false;
            }
            numUnchoked = peerList.size();
        } else {
            Set<Integer> toUnchoke 
                    = BitLibrary.getRandomSet(MAX_UNCHOKED, 0, peerList.size());
            for (Integer i : toUnchoke) {
                sendMessage(peerList.get(i), unchoke);
                peerList.get(i).remoteIsChoked = false;
            }
            numUnchoked = MAX_UNCHOKED;
        }

        while (true) {
            // accept connection to new peer (if any)
            synchronized (welcomeQ) {
                // avoid busy-wait with no peers
                while (welcomeQ.isEmpty() && peerList.isEmpty()) {
                    try {
                        logOutput(BitLibrary.getTimeString() 
                                  + ": WAITING FOR PEERS");
                        welcomeQ.wait();
                    } catch (InterruptedException ex) {
                    }
                }
                // clear the empty queue by accepting new peers
                while (!welcomeQ.isEmpty()) {
                    Socket peerSocket = welcomeQ.poll();
                    BitPeer peer = new BitPeer(peerSocket);
                    
                    if (peer.receiveHandshake(infoBencoded) == 0) {
                        // add to peerList
                        logOutput(BitLibrary.getTimeString() + ": ADDED PEER AT "
                                  + peer.getIP());
                        peerList.add(peer);
                        // complete the handshake
                        peer.sendHandshake(infoBencoded);
                        logOutput(BitLibrary.getTimeString() 
                                  + ": COMPLETED HANDSHAKE WITH "+peer.getIP());
                        // send bitfield
                        BitMessage bitfieldMsg 
                               = new BitMessage(BitMessage.MessageType.BITFIELD,
                                       BitLibrary.booleanToBits(localBitfield));
                        sendMessage(peer, bitfieldMsg);
                        // unchoke if spots are available
                        if (numUnchoked < MAX_UNCHOKED) {
                            peer.remoteIsChoked = false;
                            sendMessage(peer, unchoke);
                            numUnchoked++;
                        }
                    }
                    
                }
            }

            // process one outstanding message for each peer
            for (BitPeer peer : peerList) {
                BitMessage msg = peer.getNextMessage();
                if (msg == null) {
                    continue;
                }

                // parse the message type and process accordingly
                logOutput(BitLibrary.getTimeString() + ": RECEIVED MESSAGE TYPE "
                                     + msg.getType() + " FROM " + peer.getIP());
                peer.updateLastUsed();
                if (msg.getType() == BitMessage.MessageType.KEEP_ALIVE) {
                    // already updated lastUsed
                } else if (msg.getType() == BitMessage.MessageType.CHOKE) {
                    logDebug("CHOKE Message");
                    peer.localIsChoked = true;
                } else if (msg.getType() == BitMessage.MessageType.UNCHOKE) {
                    logDebug("UNCHOKE Message");
                    peer.localIsChoked = false;
                } else if (msg.getType() == BitMessage.MessageType.INTERESTED) {
                    logDebug("INTERESTED Message");
                    peer.remoteIsInterested = true;
                } else if (msg.getType() == BitMessage.MessageType.UNINTERESTED) {
                    logDebug("UNINTERESTED Message");
                    peer.remoteIsInterested = false;
                } else if (msg.getType() == BitMessage.MessageType.HAVE) {
                    peer.addToBitfield(msg.getIndex());
                    logOutput(BitLibrary.getTimeString() 
                              + ": PEER " + peer.getIP()
                              + " HAS " 
                              + BitLibrary.getBitString(peer.getBitfield()));
                    // say interested if we don't have this piece
                    if (localBitfield[msg.getIndex()] == false) {
                        sendMessage(peer,
                            new BitMessage(BitMessage.MessageType.INTERESTED));
                    } else if (BitLibrary.isAllTrue(peer.getBitfield())) {
                        // make room for others if peer is now seeder
                        if (peer.remoteIsChoked == false) {
                            peer.remoteIsChoked = true;
                            sendMessage(peer, new BitMessage(BitMessage.MessageType.CHOKE));
                            --numUnchoked;
                        }
                    }
                } else if (msg.getType() == BitMessage.MessageType.BITFIELD) {
                    boolean[] bf = BitLibrary.bitsToBoolean(msg.getBitfield(), numPieces);
                    peer.setBitfield(bf);
                    logOutput(BitLibrary.getTimeString() 
                              + ": PEER " + peer.getIP()
                              + " HAS " 
                              + BitLibrary.getBitString(peer.getBitfield()));
                } else if (msg.getType() == BitMessage.MessageType.REQUEST) {
                    logDebug("REQUEST Message: Peer wants piece " + msg.getIndex());
                    if (peer.remoteIsChoked) {
                        logDebug("But peer is choked, not sending");
                    } else {
                        BitMessage reply = null;
                        // make sure client has this piece
                        if (localBitfield[msg.getIndex()] == false) {
                            // peer has incorrect bitfield info, send another
                            logDebug("warning: peer incorrectly thinks we have " + msg.getIndex());
                            reply = new BitMessage(BitMessage.MessageType.BITFIELD,
                                           BitLibrary.booleanToBits(localBitfield));
                        // read the piece from the file
                        } else {
                            byte[] replyData = new byte[msg.getBlockLength()];
                            int numRead = 0;
                            try {
                                file.seek(msg.getBegin());
                                numRead = file.read(replyData, 0, msg.getBlockLength());
                                logDebug("Read " + numRead + " bytes from file");
                            } catch (IOException ex) {
                                ex.printStackTrace();
                            }
                            reply = new BitMessage(BitMessage.MessageType.PIECE,
                                        msg.getIndex(), msg.getBegin(), replyData);
                        }

                        sendMessage(peer, reply);
                        logOutput(BitLibrary.getTimeString() 
                              + ": SENT PIECE " + msg.getIndex() 
                              + " TO " + peer.getIP());
                    }
                } else if (msg.getType() == BitMessage.MessageType.PIECE) {
                    if (localBitfield[msg.getIndex()]) {
                        logDebug("warning: received piece already had");
                        continue;
                    }
                    // seek and write in the file
                    try {
                        file.seek(msg.getBegin());
                        file.write(msg.getBlock());
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                    // update bitfield, send HAVE response to ALL peers
                    localBitfield[msg.getIndex()] = true;
                    BitMessage haveMsg 
                                   = new BitMessage(BitMessage.MessageType.HAVE,
                                                    msg.getIndex());
                    for (BitPeer p : peerList) {
                        sendMessage(p, haveMsg);
                    }
                    logOutput(BitLibrary.getTimeString() + ": NOW HAVE "
                                + BitLibrary.getBitString(localBitfield));

                    // become a seeder if all downloaded
                    if (BitLibrary.isAllTrue(localBitfield)) {
                        logOutput(BitLibrary.getTimeString() + ": DOWNLOAD COMPLETE");
                        logDebug("local bitfield " 
                                 + BitLibrary.getBitString(localBitfield));
                        isSeeder = true;
                    }
                } else if (msg.getType() == BitMessage.MessageType.CANCEL) {
                    // used in "end game" mode, not implemented in this project
                } else {
                    throw new RuntimeException("Invalid MessageType received");
                }
            }
            // (ii): update interested status
            for (BitPeer peer : peerList) {
                if (!peer.localIsInterested 
                    && peer.getRarePiece(localBitfield) > -1) {
                    peer.localIsInterested = true;
                    BitMessage msg 
                            = new BitMessage(BitMessage.MessageType.INTERESTED);
                    sendMessage(peer, msg);
                }
            }

            // (iii): request pieces from all unchoked peers
            if (!isSeeder) {    // missing at least one piece
                for (BitPeer peer : peerList) {
                    int index;
                    if (!peer.localIsChoked && peer.localIsInterested
                        && (index = peer.getRarePiece(localBitfield)) > -1
                        && !peer.outstandingRequests.contains(index)) {
                        int indexLength = pieceLength;
                        if (index ==numPieces-1 && fileLength%pieceLength > 0) {
                            indexLength = fileLength % pieceLength;
                        }
                        BitMessage request 
                                = new BitMessage(BitMessage.MessageType.REQUEST,
                                       index, index * pieceLength, indexLength);
                        peer.outstandingRequests.add(index);
                        sendMessage(peer, request);
                    }
                }
            }
            // insert pauses for debugging
            if (runSlowly) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ex) {

                }
            }
        }
    }

    /* sendMessage:  send a BitMessage to the specified peer */
    public static void sendMessage(BitPeer peer, BitMessage msg) {
        byte[] packedMsg = msg.pack();
        peer.write(packedMsg, 0, packedMsg.length);
        // log sent message event
        StringBuilder sb = new StringBuilder();
        sb.append(BitLibrary.getTimeString() + ": SENT " + msg.getType());
        if (msg.getType() == BitMessage.MessageType.REQUEST
            || msg.getType() == BitMessage.MessageType.PIECE) {
            sb.append(" FOR " + msg.getIndex());
        }
        sb.append(" TO " + peer.getIP());
        logOutput(sb.toString());
    }

    /* parseArgs:  create saveFile for writing, get torrent metadata */
    /* return -1 on failure and 0 otherwise */
    public static int parseArgs(String[] args) {
        peerList = new ArrayList<BitPeer>();
        if (args.length == 0 || args.length % 2 == 0 
            || BitLibrary.hasStr(args, "-h")) {
            logError("usage: java BitClient [FLAGS]* torrentFile");
            logError("\t-h         \t Usage information");
            logError("\t-s saveFile\t Specify save location");
            logError("\t-p IP:port \t Include this address as a peer");
            logError("\t-v [on|off]\t Verbose on/off");
            logError("\t-w port    \t Welcome socket port number");
            logError("\t-x seed    \t Start this client as seeder");
            logError("\t-z slow    \t Run in slow motion for testing");
            return -1;
        }

        for (int i = 0; i < args.length - 1; i += 2) {
            if (args[i].equals("-s")) {
                savePath = args[i+1];
            } else if (args[i].equals("-p")) {
                // add a peer to the list
                InetAddress peerAddr = null;
                int peerPort = 0;
                try {
                    int delimPos = args[i+1].indexOf(':', 0);
                    String ipString = args[i+1].substring(0, delimPos);
                    String portString = args[i+1].substring(delimPos + 1);

                    peerAddr = InetAddress.getByName(ipString);
                    peerPort = Integer.parseInt(portString);
                    peerList.add(new BitPeer(peerAddr, peerPort));
                } catch (UnknownHostException|NumberFormatException ex) {
                    logError("error: unknown IP:port " + args[i+1]);
                    return -1;
                }
                logDebug("Added Peer: IP = " + peerAddr + ", " 
                         + "Port = " + peerPort);
            } else if (args[i].equals("-v")) {
                if (args[i+1].equals("on")) {
                    _DEBUG = true;
                } else {
                    _DEBUG = false;
                }
            } else if (args[i].equals("-w")) {
                try {
                    welcomePort = Integer.parseInt(args[i+1]);
                } catch (NumberFormatException ex) {
                    logError("error: invalid welcome port " + args[i+1]);
                    return -1;
                }
            } else if (args[i].equals("-x")) {
                isSeeder = true;
                // file to transfer found at savePath
            } else if (args[i].equals("-z")) {
                runSlowly = true;
            }
        }
        /* read torrent file data */
        try {
            String torrentName = TRNT_DIR + args[args.length - 1];
            byte[] torrentData = Files.readAllBytes(Paths.get(torrentName));
            encoded = new String(torrentData, "US-ASCII");
            encoded = encoded.trim();
        } catch (IOException ex) {
            logError("error: cannot open " + args[args.length - 1]);
            return -1;
        }

        return 0;
    }

    /* initClient: parse file metadata from METAINFO */
    /* return: 0 on success, -1 on failure */
    /* success ==> initialized: fileLength, pieceLength, saveBuf, pieces */
    public static int initClient() {
        BObject[] metainfo = BDecoder.read(encoded);
        if (metainfo.length != 1) {
            logError("error: invalid .torrent file");
            return -1;
        }
        BDict metaDict = (BDict) metainfo[0];
        // (a) parse the info dictionary within metaDict
        if (metaDict.containsKey("info")) {
            BDict infoDict = (BDict) metaDict.get("info");
            infoBencoded = infoDict.encode();

            // (i) length field
            BObject len = infoDict.get("length");
            if (len == null) {
                logError("error: invalid length in .torrent file");
                return -1;
            }
            fileLength = Integer.parseInt(len.print());
            logDebug("got fileLength " + fileLength);

            // (ii) piece length field
            BObject plen = infoDict.get("piece length");
            if (plen == null) {
                logError("error: invalid piece length in .torrent file");
                return -1;
            }
            pieceLength = Integer.parseInt(plen.print());
            logDebug("got pieceLength " + pieceLength);
            numPieces = fileLength / pieceLength;
            if (fileLength % pieceLength > 0) {
                ++numPieces;
            }
            logDebug("got numPieces " + numPieces);

            // (iii) suggested save name field ==> save at DNLD_DIR/<sug_name>
            BObject sname = infoDict.get("name");
            if (sname != null && savePath == null) {    // -s flag not used
                savePath = DNLD_DIR + sname.print();
                logDebug("got savePath " + savePath);
            }

            // (iv) SHA1 values for pieces
            BObject sha = infoDict.get("pieces");
            if (sha == null) {
                logError("error: invalid SHA1 encoding of pieces");
                return -1;
            }
            String piecesSHA1 = sha.print();
            if (piecesSHA1.length() % SHA_LENGTH != 0) {
                logError("error: SHA1 length not divisible by 20");
                return -1;
            } else {
                // split the SHA1 hashes into arrayList
                pieces = new String[piecesSHA1.length() / SHA_LENGTH];
                for (int i = 0; i < pieces.length; ++i) {
                    String s = piecesSHA1.substring(SHA_LENGTH * i, 
                                                    SHA_LENGTH * (i + 1));
                    byte[] hashData;
                    try {
                        hashData = s.getBytes("UTF-8");
                    } catch (UnsupportedEncodingException ex) {
                        ex.printStackTrace();
                        return -1;
                    }
                    pieces[i] = BitLibrary.bytesToHex(hashData);
                }
                if (_DEBUG) {
                    logDebug("Got the following SHA1 pieces:");
                    if (_DEBUG) {
                        for (int i = 0; i < pieces.length; ++i) {
                            logDebug(pieces[i]);
                        }
                    }
                }
            }

            // (v) bitfield
            localBitfield = new boolean[numPieces];
            if (isSeeder) {
                logDebug("I AM A SEEDER");
            }
            for (int i = 0; i < localBitfield.length; ++i) {
                localBitfield[i] = isSeeder;   // all true if seeder, else false
            }
        } else {
            logError("error: no info field specified in .torrent file");
            return -1;
        }
        // (b) get tracker URL
        BObject tracker = metaDict.get("announce");
        if (tracker != null) {
            trackerURL = tracker.print();
        }
        logDebug("got tracker URL " + trackerURL);
        // (c) initialize torrent file for reading/writing
        if (savePath == null) {
            logError("error: no save location specified");  // .torrent nor CLI
            return -1;
        }
        if (isSeeder) {
            // change to UPLD_DIR for a seeder
            savePath = savePath.substring(savePath.lastIndexOf('/') + 1);
            savePath = UPLD_DIR + savePath;
            logDebug("Seeder now has savePath = " + savePath);
            // make sure file exists and has proper length
            File source = new File(savePath);
            if (!source.isFile()) {
                logError("error: seeder does not have " + savePath);
                return -1;
            } else if (source.length() != fileLength) {
                logError("error: file length differs from torrent file specs");
                return -1;
            }
            try {
                file = new RandomAccessFile(source, "r");
                logDebug("Seeder opened file at " + source);
            } catch (IOException ex) {
                logError("error: seeder could not open " + savePath);
                return -1;
            }
        } else {
            try {
                file = new RandomAccessFile(savePath, "rw");
                file.setLength(fileLength);
                logDebug("Leecher opened new file at " + savePath);
            } catch (IOException ex) {
                logError("error: client could not open " + savePath);
                return -1;
            }
        }
            
        // (d) set up welcomer thread
        welcomeQ = new LinkedList<Socket>();
        welcomer = new BitWelcomer(welcomePort, welcomeQ);
        welcomer.start();

        return 0;
    }

    public static void logError(String str) {
        System.err.println(str);
    }

    public static void logDebug(String str) {
        if (_DEBUG) {
            System.err.println(str);
        }
    }

    public static void logOutput(String str) {
        System.out.println(str);
    }
}
