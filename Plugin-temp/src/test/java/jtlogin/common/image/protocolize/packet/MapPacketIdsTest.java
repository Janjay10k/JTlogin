/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package jtlogin.common.image.protocolize.packet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MapPacketIdsTest {

    private static int idFor(int protocol) {
        return MapPacketIds.SINCE_1_21_2.stream()
                .filter(r -> protocol >= r.fromProtocol() && protocol <= r.toProtocol())
                .mapToInt(MapPacketIds.Range::packetId)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no map packet id for protocol " + protocol));
    }

    @Test
    void everyProtocolFrom768To777HasExactlyOneId() {
        for (int protocol = 768; protocol <= MapPacketIds.NEWEST_KNOWN_PROTOCOL; protocol++) {
            int finalProtocol = protocol;
            long matches = MapPacketIds.SINCE_1_21_2.stream()
                    .filter(r -> finalProtocol >= r.fromProtocol() && finalProtocol <= r.toProtocol())
                    .count();
            assertEquals(1, matches, "protocol " + protocol);
        }
    }

    @Test
    void rangesAreOrderedAndNonOverlapping() {
        int previousEnd = 767; // 1.21.1, the last version handled by MapDataPacket itself
        for (var range : MapPacketIds.SINCE_1_21_2) {
            assertEquals(previousEnd + 1, range.fromProtocol(), range.versions());
            assertTrue(range.toProtocol() >= range.fromProtocol(), range.versions());
            previousEnd = range.toProtocol();
        }
        assertEquals(777, previousEnd);
    }

    @Test
    void idsForTheTargetRange() {
        assertEquals(0x31, idFor(774), "Minecraft 1.21.11");
        assertEquals(0x33, idFor(775), "Minecraft 26.1 / 26.1.1 / 26.1.2");
        assertEquals(0x33, idFor(776), "Minecraft 26.2");
        assertEquals(0x34, idFor(777), "Minecraft 26.3");
    }

    @Test
    void idsFor1_21_2To1_21_10() {
        assertEquals(0x2d, idFor(768));
        assertEquals(0x2d, idFor(769));
        assertEquals(0x2c, idFor(770));
        assertEquals(0x2c, idFor(771));
        assertEquals(0x2c, idFor(772));
        assertEquals(0x31, idFor(773));
    }
}
