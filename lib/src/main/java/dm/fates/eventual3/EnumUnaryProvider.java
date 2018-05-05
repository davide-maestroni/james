package dm.fates.eventual3;

/**
 * Created by davide-maestroni on 05/02/2018.
 */
public interface EnumUnaryProvider<P1, R> {

  Eventual<R> get(P1 firstParam, long count) throws Exception;
}
