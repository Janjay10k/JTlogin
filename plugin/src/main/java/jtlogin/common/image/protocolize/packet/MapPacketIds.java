/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package jtlogin.common.image.protocolize.packet;

import java.util.List;

/**
 * Clientbound "Map Data" packet ids for Minecraft 1.21.2 and newer, by protocol version.
 * <p>
 * The ids for older versions (1.13 - 1.21.1) are inherited from LibreLogin and live in {@link MapDataPacket}.
 * <p>
 * Provenance: derived from the clientbound packet tables of PacketEvents (the id of a packet is its position in
 * the table for the protocol revision). The same derivation reproduces every id that LibreLogin already used for
 * 1.13 - 1.21.1, which is how the method was validated. Table to protocol assignment: 1.21.2 table = 768-769,
 * 1.21.5 = 770, 1.21.6 = 771-772, 1.21.9 = 773-774, 26.1 = 775-776, 26.3 = 777.
 * The packet body layout for the icon-less map data JTLogin sends is unchanged since 1.17.
 * <p>
 * Status: derived from source, NOT yet confirmed against a real client for every version.
 */
public final class MapPacketIds {

    public record Range(int fromProtocol, int toProtocol, int packetId, String versions) {
    }

    public static final List<Range> SINCE_1_21_2 = List.of(
            new Range(768, 769, 0x2d, "1.21.2 - 1.21.4"),
            new Range(770, 770, 0x2c, "1.21.5"),
            new Range(771, 772, 0x2c, "1.21.6 - 1.21.8"),
            new Range(773, 774, 0x31, "1.21.9 - 1.21.11"),
            new Range(775, 776, 0x33, "26.1 - 26.2"),
            new Range(777, 777, 0x34, "26.3")
    );

    /**
     * The newest protocol version for which an id is known. Clients newer than this get no captcha map until
     * a new range is added.
     */
    public static final int NEWEST_KNOWN_PROTOCOL = 777;

    private MapPacketIds() {
    }
}
