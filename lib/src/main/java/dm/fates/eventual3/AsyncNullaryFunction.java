package dm.fates.eventual3;

/**
 * Created by davide-maestroni on 04/26/2018.
 */
public interface AsyncNullaryFunction<R> {

  Eventual<R> call() throws Exception;
}
