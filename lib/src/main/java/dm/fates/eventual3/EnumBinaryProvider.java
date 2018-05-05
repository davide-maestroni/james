package dm.fates.eventual3;

/**
 * Created by davide-maestroni on 05/02/2018.
 */
public interface EnumBinaryProvider<P1, P2, R> {

  Eventual<R> get(P1 firstParam, P2 secondParam, long count) throws Exception;
}
