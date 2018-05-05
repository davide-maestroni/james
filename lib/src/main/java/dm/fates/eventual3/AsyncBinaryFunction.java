package dm.fates.eventual3;

/**
 * Created by davide-maestroni on 04/26/2018.
 */
public interface AsyncBinaryFunction<P1, P2, R> {

  Eventual<R> call(P1 firstParam, P2 secondParam) throws Exception;
}
