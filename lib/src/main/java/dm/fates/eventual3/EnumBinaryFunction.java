package dm.fates.eventual3;

/**
 * Created by davide-maestroni on 04/26/2018.
 */
public interface EnumBinaryFunction<P1, P2, V, R> {

  Eventual<R> call(P1 firstParam, P2 secondParam, long count, V value) throws Exception;
}
