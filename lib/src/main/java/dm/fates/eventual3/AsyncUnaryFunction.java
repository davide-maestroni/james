package dm.fates.eventual3;

/**
 * Created by davide-maestroni on 04/26/2018.
 */
public interface AsyncUnaryFunction<P1, R> {

  Eventual<R> call(P1 firstParam) throws Exception;
}
