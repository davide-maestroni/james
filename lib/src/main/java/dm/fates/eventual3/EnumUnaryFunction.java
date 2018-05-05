package dm.fates.eventual3;

/**
 * Created by davide-maestroni on 04/26/2018.
 */
public interface EnumUnaryFunction<P1, V, R> {

  Eventual<R> call(P1 firstParam, long count, V value) throws Exception;
}
