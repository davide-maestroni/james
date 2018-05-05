package dm.fates.eventual3;

/**
 * Created by davide-maestroni on 05/02/2018.
 */
public interface EnumNullaryProvider<R> {

  Eventual<R> get(long count) throws Exception;
}
