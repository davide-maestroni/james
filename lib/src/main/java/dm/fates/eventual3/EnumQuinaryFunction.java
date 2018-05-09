package dm.fates.eventual3;

/**
 * Created by davide-maestroni on 04/26/2018.
 */
public interface EnumQuinaryFunction<P1, P2, P3, P4, P5, V, R> {

  Eventual<R> call(P1 firstParam, P2 secondParam, P3 thirdParam, P4 fourthParam, P5 fifthParam,
      long count, V value) throws Exception;
}
