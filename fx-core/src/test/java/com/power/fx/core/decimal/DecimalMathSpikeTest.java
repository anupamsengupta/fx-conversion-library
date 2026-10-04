package com.power.fx.core.decimal;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.MathContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Task 2.1 correctness gate (implementation plan Section 4, Task 2.1
 * acceptance items 1-3). This is the module-local, throwaway rehearsal of
 * what {@code fx-testkit}'s {@code DecimalMathConformanceTest} will later
 * ship as the authoritative suite (Phase 3b, Task 3b.6) -- see Section 2.1
 * of the plan ("shift-left architecture gate").
 *
 * <p><strong>Provisional reference set, non-authoritative</strong> (TI-06):
 * the 50-plus points below were computed offline via Python's
 * {@code decimal.Decimal.ln()}/{@code .exp()} (a correctly-rounded,
 * arbitrary-precision, non-floating-point implementation) at 80 digits.
 * This is <em>not</em> the MPFR/mpmath-cross-checked, 2,000-point,
 * independently-verified table Appendix D.5 describes as the Phase 3b
 * deliverable -- it is this task's own provisional, clearly-labelled
 * substitute, used only to clear the Phase 2 gate.
 */
class DecimalMathSpikeTest {

    private static final MathContext WORKING = new MathContext(60, java.math.RoundingMode.HALF_EVEN);
    private static final BigDecimal RELATIVE_TOLERANCE = new BigDecimal("1E-33");

    // value | ln(value), both computed offline via Python decimal.Decimal.ln() at 80 digits.
    private static final String[][] LN_REFERENCE = {
            {"1E-30", "-69.077552789821370520539743640530926228033044658863189280999837029027178290320574"},
            {"2.5E-25", "-56.648336592976987035266259155341094118577435995811061933041229642208529876195849"},
            {"7.3E-20", "-44.063827511726568235285711690933105788718378416332634832104187063157234827303805"},
            {"9.999E-15", "-32.236291306916972934587213865595385870812256238537392739980680479308732880239546"},
            {"1.0001E-10", "-23.025750934940123531844581380162690944947532821886658684820617715354081180699453"},
            {"3.14159E-7", "-14.973366609773141406909527602354389994417117612056720361945951191282977529403808"},
            {"0.00005", "-9.9034875525361280454891979401956333984799060888753471582539916133636840606791046"},
            {"0.00666", "-5.0116357944298392835141390070871458967349486059704745589316581468764910929894887"},
            {"0.01", "-4.6051701859880913680359829093687284152022029772575459520666558019351452193547050"},
            {"0.25", "-1.3862943611198906188344642429163531361510002687205105082413600189867872439393894"},
            {"7.3", "1.9878743481543454450741174027541783633036513562428246885623709561942173662432448"},
            {"9.999", "2.3024850879937123256826579546700772432042660908942019005192380352048562649207411"},
            {"10.0010", "2.3026850879943789923533246213653153386645835530298440515459891952892175257514242"},
            {"31.41590", "3.4473141341792240652344040351205236663916942969734634463206720164576033480150116"},
            {"500", "6.2146080984221917426367422425949160547278043315260636739793036934093242070623627"},
            {"6660.00", "8.8038747635344348205938097210190393488716603258021632972683092589289445650746262"},
            {"100000", "11.512925464970228420089957273421821038005507443143864880166639504837863048386762"},
            {"25000000.0", "17.034386382832474853309467394558560524657811640309673300025263188753793633479430"},
            {"73000000000.0", "25.013725278094802285254031949597820439314666242530554448895649965869943463016770"},
            {"9999000000000000.000", "36.841261482904397585952529774935540357220788420325796541019156549718445410081028"},
            {"100010000000000000000.0000", "46.051801854881246988695162260368235283085511836976530596179219313673097109621121"},
            {"31415900000000000000000000.00000", "58.709356366036320481666198947545264648818130024064014871120541639679345980271471"},
            {"5000000000000000000000000000000", "70.686990702255470895140502973757113867558646013131707002912484920501357278028232"},
            {"1", "0"},
            {"10", "2.3025850929940456840179914546843642076011014886287729760333279009675726096773525"},
            {"100", "4.6051701859880913680359829093687284152022029772575459520666558019351452193547050"},
            {"0.1", "-2.3025850929940456840179914546843642076011014886287729760333279009675726096773525"},
    };

    // argument | exp(argument), both computed offline via Python decimal.Decimal.exp() at 80 digits.
    private static final String[][] EXP_REFERENCE = {
            {"-70", "3.9754497359086468077890997537948254523324502696237932908414605435252536609041596E-31"},
            {"-65", "5.9000905415970613914012602955584225320361532444094254039672193164104598588134496E-29"},
            {"-60", "8.7565107626965203384887328007391660365571074817817589060567154238794020267333768E-27"},
            {"-50", "1.9287498479639177830173428165270125747528326512302629108978091038205116249796466E-22"},
            {"-40", "4.2483542552915889953292347828586580178795655541664462880508189189260330639269147E-18"},
            {"-30", "9.3576229688401746049158322233787067449583226889358804164133186199608428337676169E-14"},
            {"-20", "2.0611536224385578279659403801558209763758072755991036929722446616291640237845594E-9"},
            {"-15", "3.0590232050182578837147949770228963937082078081855911655926182739785843137401116E-7"},
            {"-10", "0.000045399929762484851535591515560550610237918088866564969259071305650999421614302282"},
            {"-7", "0.00091188196555451620800313608440928262647372452743605384081613342188947988931030653"},
            {"-5", "0.0067379469990854670966360484231484242488495850273550854303055315726835225156040623"},
            {"-3", "0.049787068367863942979342415650061776631699592188423215567627727606060667730199550"},
            {"-2", "0.13533528323661269189399949497248440340763154590957588146815887265407337410148769"},
            {"-1", "0.36787944117144232159552377016146086744581113103176783450783680169746149574489980"},
            {"-0.5", "0.60653065971263342360379953499118045344191813548718695568289215873505651941374842"},
            {"0.5", "1.6487212707001281468486507878141635716537761007101480115750793116406610211942156"},
            {"1", "2.7182818284590452353602874713526624977572470936999595749669676277240766303535476"},
            {"2", "7.3890560989306502272304274605750078131803155705518473240871278225225737960790578"},
            {"3", "20.085536923187667740928529654581717896987907838554150144378934229698845878091974"},
            {"5", "148.41315910257660342111558004055227962348766759387898904675284511091206482095858"},
            {"7", "1096.6331584284585992637202382881214324422191348336131437827392407761217693312331"},
            {"10", "22026.465794806716516957900645284244366353512618556781074235426355225202818570793"},
            {"15", "3269017.3724721106393018550460917213155057385438200342066295627732420213327488791"},
            {"20", "485165195.40979027796910683054154055868463898894484725435361080031597799614270974"},
            {"30", "10686474581524.462146990468650741401650024495005473054990222911492108452944787132"},
            {"40", "235385266837019985.40789991074903480450887161725455546723665125118928916352581695"},
            {"50", "5184705528587072464087.4533229334853848274691005838464019040569338068568847937954"},
            {"60", "114200738981568428366295718.31447656301980459595563958395650279917582048588847634"},
            {"65", "16948892444103337141417836114.371974948926236225516504913157269645316241620401983"},
            {"70", "2515438670919167006265781174252.1129614074129733831405138218401569861104577954931"},
    };

    @Test
    void lnMatchesIndependentReferenceWithinWorkingPrecision() {
        for (String[] row : LN_REFERENCE) {
            BigDecimal x = new BigDecimal(row[0]);
            BigDecimal expected = new BigDecimal(row[1]);
            BigDecimal actual = DecimalLn.ln(x, WORKING);
            assertRelativeError(expected, actual, new BigDecimal("1E-50"), "ln(" + x + ")");
        }
    }

    @Test
    void expMatchesIndependentReferenceWithinWorkingPrecision() {
        for (String[] row : EXP_REFERENCE) {
            BigDecimal x = new BigDecimal(row[0]);
            BigDecimal expected = new BigDecimal(row[1]);
            BigDecimal actual = DecimalExp.exp(x, WORKING);
            assertRelativeError(expected, actual, new BigDecimal("1E-50"), "exp(" + x + ")");
        }
    }

    // Appendix D.2's error budget (<1e-55 at working=60) assumes every intermediate
    // operation runs at WORKING precision, with DECIMAL128 rounding applied once, as
    // the final step (D.2 step 6 / FxMath.lnPublished). Running the whole algorithm
    // directly at 34-digit precision (i.e. passing DECIMAL128 as the working context)
    // loses that margin -- empirically ~4.8e-32 relative error on a single round trip,
    // outside the 1e-33 contract. FxMath.lnPublished/expPublished is therefore the
    // correct unit of accuracy, not DecimalLn.ln(x, FxMath.DECIMAL128) directly.
    //
    // Composed round trip keeps the intermediate at WORKING precision (not rounded to
    // DECIMAL128 mid-flight) and rounds only the final composed value once -- matching
    // D.1's "the DECIMAL128-rounded result of ln and exp" (singular final result) rather
    // than compounding two independent 34-digit roundings, which (as the first version of
    // this test discovered) can accumulate 2-10 ulps at the 1e-30 extreme and breach the
    // 1e-33 bound through rounding alone, not through any algorithmic inaccuracy.
    @Test
    void expOfLnRoundTripsAtDecimal128() {
        BigDecimal[] xs = {
                new BigDecimal("1E-30"), new BigDecimal("0.0001"), new BigDecimal("0.5"),
                new BigDecimal("1"), new BigDecimal("2"), new BigDecimal("10"),
                new BigDecimal("12345.6789"), new BigDecimal("1E30"),
        };
        for (BigDecimal x : xs) {
            BigDecimal lnX = DecimalLn.ln(x, WORKING);
            BigDecimal roundTrip = DecimalExp.exp(lnX, WORKING).round(FxMath.DECIMAL128);
            assertRelativeError(x, roundTrip, RELATIVE_TOLERANCE, "exp(ln(" + x + "))");
        }
    }

    @Test
    void lnOfExpRoundTripsAtDecimal128() {
        BigDecimal[] xs = {
                new BigDecimal("-70"), new BigDecimal("-10"), new BigDecimal("-1"),
                new BigDecimal("0"), new BigDecimal("1"), new BigDecimal("10"), new BigDecimal("69.5"),
        };
        for (BigDecimal x : xs) {
            BigDecimal expX = DecimalExp.exp(x, WORKING);
            BigDecimal roundTrip = DecimalLn.ln(expX, WORKING).round(FxMath.DECIMAL128);
            BigDecimal diff = roundTrip.subtract(x, FxMath.DECIMAL128).abs(FxMath.DECIMAL128);
            assertTrue(diff.compareTo(RELATIVE_TOLERANCE) < 0,
                    "ln(exp(" + x + ")) = " + roundTrip + " too far from " + x);
        }
    }

    @Test
    void algebraicIdentities() {
        BigDecimal a = new BigDecimal("3.14159");
        BigDecimal b = new BigDecimal("2.71828");

        BigDecimal lnA = DecimalLn.ln(a, WORKING);
        BigDecimal lnB = DecimalLn.ln(b, WORKING);
        BigDecimal lnAB = DecimalLn.ln(a.multiply(b, WORKING), WORKING);
        assertRelativeError(lnAB, lnA.add(lnB, WORKING), new BigDecimal("1E-50"), "ln(a*b) == ln a + ln b");

        BigDecimal expA = DecimalExp.exp(a, WORKING);
        BigDecimal expB = DecimalExp.exp(b, WORKING);
        BigDecimal expAB = DecimalExp.exp(a.add(b, WORKING), WORKING);
        assertRelativeError(expAB, expA.multiply(expB, WORKING), new BigDecimal("1E-50"),
                "exp(a+b) == exp a * exp b");

        for (int k = -5; k <= 5; k++) {
            BigDecimal tenToK = BigDecimal.TEN.pow(Math.abs(k), WORKING);
            BigDecimal x = k >= 0 ? tenToK : BigDecimal.ONE.divide(tenToK, WORKING);
            BigDecimal lnX = DecimalLn.ln(x, WORKING);
            BigDecimal kLn10 = BigDecimal.valueOf(k).multiply(DecimalConstants.LN10, WORKING);
            assertRelativeError(kLn10, lnX, new BigDecimal("1E-50"), "ln(10^" + k + ") == k*LN10");
        }

        assertEquals(BigDecimal.ZERO, DecimalLn.ln(BigDecimal.ONE, WORKING), "ln(1) == 0 exactly");
    }

    @Test
    void constantSelfCheck() {
        BigDecimal expLn10 = DecimalExp.exp(DecimalConstants.LN10, WORKING);
        assertRelativeError(BigDecimal.TEN, expLn10, new BigDecimal("1E-50"), "exp(LN10) == 10");

        BigDecimal lnTen = DecimalLn.ln(BigDecimal.TEN, WORKING);
        assertRelativeError(DecimalConstants.LN10, lnTen, new BigDecimal("1E-50"), "ln(10) == LN10");
    }

    @Test
    void decimalSqrtNeverUsesFloatingPoint() {
        // DecimalSqrt is implemented purely over BigInteger.sqrt(); this test
        // exercises a range of magnitudes and checks against squaring back.
        BigDecimal[] values = {
                new BigDecimal("2"), new BigDecimal("0.0001"), new BigDecimal("123456789.987654321"),
                new BigDecimal("1E-20"), new BigDecimal("1E20"),
        };
        for (BigDecimal v : values) {
            BigDecimal root = DecimalSqrt.sqrt(v, WORKING);
            BigDecimal squared = root.multiply(root, WORKING);
            assertRelativeError(v, squared, new BigDecimal("1E-55"), "sqrt(" + v + ")^2");
        }
    }

    private static void assertRelativeError(BigDecimal expected, BigDecimal actual, BigDecimal tolerance, String label) {
        if (expected.signum() == 0) {
            assertTrue(actual.abs(FxMath.DECIMAL128).compareTo(tolerance) < 0, label + ": expected 0, got " + actual);
            return;
        }
        BigDecimal relError = actual.subtract(expected, WORKING).abs(WORKING)
                .divide(expected.abs(WORKING), WORKING);
        assertTrue(relError.compareTo(tolerance) < 0,
                label + ": relative error " + relError + " exceeds " + tolerance
                        + " (expected=" + expected + ", actual=" + actual + ")");
    }
}
