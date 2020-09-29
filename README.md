# Beaver BitTorrent
This is an ongoing project to develop a simplified BitTorrent client from scratch, with the long-term goal being a stand-alone client that seeds with swarms in the wild. As it currently stands, the following simplifications are in place:

1. The client doesn't use standard choking/unchoking algorithms. It uses random unchoking of a maximum of four peers at a time.
2. Integration with a tracker remains future work. Instead the peers and their listen ports are given on the command line (see HubbleTransferTest and BigTxtTransferTest for examples).
3. The system is not robust to invalid command line arguments, etc. It is also not secure. These points remain for future work.

## Design Points of Interest
* This client implements the core BitTorrent transfer mechanism. That is, it devides a file into chunks, connects to other peers in the swarm, and transfers pieces in random order to other clients at their request. The file is assembled out-of-order, but ends up being a lossless download from the swarm.
* The client parses [Bencoded](https://en.wikipedia.org/wiki/Bencode) .torrent files as they exist in the wild.
* To handle non-blocking reading and writing from sockets, the client maintains a separate reader thread for each peer connection. This thread continually reads messages from the socket and puts them on a message queue. This design decision was critical to performance (specifically it increased throughput by ~300% compared to blocking message I/O).

Please see [the official BEP 3 specification](http://www.bittorrent.org/beps/bep_0003.html) for a relatively thorough treatment of the BitTorrent protocol.

## Usage
Compile with the following command (with src as your current working directory):
`javac ./*.java ./util/lib/*.java ./util/bencode/*.java`

Tests can be found in this README directory, including the commands to run them.
You can run "% java BitClient -h" to print the following usage screen:
```
usage: java BitClient [FLAGS]* torrentFile
    -h           Usage information
    -s saveFile  Specify save location
    -p IP:port   Include this address as a peer
    -v [on|off]  Verbose on/off
    -w port      Welcome socket port number
    -x seed      Start this client as seeder
    -z slow      Run in slow motion for testing
```

## Directory Structure
* BitClient.java: Simplified BitTorrent client, core of client functionality.
* BitMessage.java: Handles packing and unpacking of BitTorrent messages.
  * Includes all the message types as specified by the BitTorrent protocol.
  * Handles portable encoding for interacting with other BitTorrent clients.
* BitPeer.java: Holds all state of a single peer connection, including a thread
    that continually reads messages, a queue of messages, and choking/interested
    status.
* BitReader.java: Runnable thread that continually reads messages into a shared
    queue for later processing. Has a maximum backlog of 10 messages.
* BitWelcomer.java: Runnable thread that continually welcomes new peer connec-
    tions and places them on a welcome queue.
* util/
  * bencode/ (Adapted from open-source code): Handles all encoding and
        parsing of .torrent files. This is only used in initial setup and is
        not part of the transfer protocol.
    * BDecoder.java: Parses bencoded .torrent file into BObject[]
    * BObject.java: Interface for a decoded metainfo object
    * BNumber.java: A decoded number object
    * BList.java: A decoded list object
    * BString.java: A decoded string object
    * BDict.java: A decoded dictionary object
  * lib/: Library of miscellaneous utility functions needed by the BitClient.
    *BitLibrary.java: Utility functions such as array conversion,
            SHA1 hash encoding, writing a ByteBuffer, and getting a timestamp.
* test/
  * torrents/: .torrent files for testing the client
    * big.txt.torrent
    * hubble.jpg.torrent
    * moby_dick.txt.torrent
    * random.txt.torrent
  * downloads/: default directory for saving downloaded files.
  * uploads/: directory where seeder finds complete files to upload.
    * big.txt (1.5 MB)
    * hubble.jpg (6.8 MB)
    * moby_dick.txt (32 KB)
    * random.txt (590 KB)
