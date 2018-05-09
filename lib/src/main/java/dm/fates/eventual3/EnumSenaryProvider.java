package dm.fates.eventual3;

/**
 * Created by davide-maestroni on 05/02/2018.
 */
public interface EnumSenaryProvider<P1, P2, P3, P4, P5, P6, R> {

  Eventual<R> get(P1 firstParam, P2 secondParam, P3 thirdParam, P4 fourthParam, P5 fifthParam,
      P6 sixthParam, long count) throws Exception;
}
