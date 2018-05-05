package dm.fates.eventual3;

/**
 * Created by davide-maestroni on 04/26/2018.
 */
public interface EnumNullaryFunction<V, R> {

  Eventual<R> call(long count, V value) throws Exception;
}
