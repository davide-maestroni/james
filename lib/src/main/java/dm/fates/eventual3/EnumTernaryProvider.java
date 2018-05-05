package dm.fates.eventual3;

/**
 * Created by davide-maestroni on 05/02/2018.
 */
public interface EnumTernaryProvider<P1, P2, P3, R> {

  Eventual<R> get(P1 firstParam, P2 secondParam, P3 thirdParam, long count) throws Exception;
}
