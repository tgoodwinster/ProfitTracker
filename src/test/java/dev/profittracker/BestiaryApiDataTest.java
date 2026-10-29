package dev.profittracker;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BestiaryApiDataTest {
    private String member(long a, long b, long nest) {
        return "{\"last_save\":100,\"bestiary\":{\"kills\":{\"endermite_37\":"+a+",\"endermite_40\":"+b+",\"nest_endermite_50\":"+nest+",\"enderman_50\":999999}},\"inventory\":{\"ignored\":[1,2,3]}}";
    }
    @Test void sumsExactlyTheEndermiteFamilyForTheRequestedCoopMember() {
        String json = "{\"success\":true,\"profiles\":[{\"profile_id\":\"blue\",\"cute_name\":\"Blueberry\",\"selected\":true,\"members\":{\"mine\":"+member(100,200,30)+",\"coop\":"+member(9000,9000,9000)+"}}]}";
        var data = BestiaryApiData.parse(json,"mine", "Blueberry");
        assertEquals(330, data.kills()); assertEquals("blue", data.profileId()); assertEquals(100, data.savedAt());
    }
    @Test void explicitProfileWinsOverOtherSelectedProfileWithoutCrossProfileAggregation() {
        String json = "{\"success\":true,\"profiles\":[{\"profile_id\":\"other\",\"cute_name\":\"Apple\",\"selected\":true,\"members\":{\"mine\":"+member(900,900,900)+"}},{\"profile_id\":\"blue\",\"cute_name\":\"Blueberry\",\"selected\":false,\"members\":{\"mine\":"+member(1,2,3)+"}}]}";
        assertEquals(6, BestiaryApiData.parse(json,"mine","Blueberry").kills());
        assertEquals(2700, BestiaryApiData.parse(json,"mine","").kills());
        assertThrows(RuntimeException.class, () -> BestiaryApiData.parse(json,"mine","Orange"));
        assertThrows(RuntimeException.class, () -> BestiaryApiData.parse(json,"unknown",""));
    }
    @Test void missingOrInvalidBestiaryIsUnavailableRatherThanAnInventedZero() {
        assertThrows(RuntimeException.class, () -> BestiaryApiData.parse("{\"success\":true,\"profiles\":[]}","mine",""));
        assertThrows(RuntimeException.class, () -> BestiaryApiData.parse("{\"success\":false}","mine",""));
        String json = "{\"success\":true,\"profiles\":[{\"profile_id\":\"p\",\"selected\":true,\"members\":{\"mine\":"+member(-1,0,0)+"}}]}";
        assertThrows(RuntimeException.class, () -> BestiaryApiData.parse(json,"mine",""));
    }
    @Test void newSaveRebasesEvenDownwardsButCachedResponsesKeepLiveIncrements() {
        var total = new BestiaryTotal();
        assertTrue(total.update(new BestiaryApiData("p","Blueberry",100,1000), 2000));
        total.kill(); total.kill(); assertEquals(102, total.total());
        assertFalse(total.update(new BestiaryApiData("p","Blueberry",100,1000), 3000));
        assertEquals(102, total.total());
        assertTrue(total.update(new BestiaryApiData("p","Blueberry",99,2000), 4000));
        assertEquals(99, total.total()); assertEquals(0, total.extra);
        assertFalse(total.update(new BestiaryApiData("p","Blueberry",150,1000), 5000));
        assertEquals(99, total.total());
    }
    @Test void nestedProfileSaveTimestampCanProveARepeatedCountIsFresh() {
        String json="{\"success\":true,\"profiles\":[{\"profile_id\":\"p\",\"selected\":true,\"members\":{\"mine\":{\"profile\":{\"last_save\":2000},\"bestiary\":{\"kills\":{\"endermite_37\":100}}}}}]}";
        var data=BestiaryApiData.parse(json,"mine","");assertEquals(2000,data.savedAt());
        var total=new BestiaryTotal();total.update(new BestiaryApiData("p","",100,1000),1000);total.kill();
        assertTrue(total.update(data,3000));assertEquals(100,total.total());
    }
}
