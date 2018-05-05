package dm.fates.eventual3;

/**
 * Created by davide-maestroni on 04/26/2018.
 */
public interface AsyncOctonaryFunction<P1, P2, P3, P4, P5, P6, P7, P8, R> {

  Eventual<R> call(P1 firstParam, P2 secondParam, P3 thirdParam, P4 fourthParam, P5 fifthParam,
      P6 sixthParam, P7 seventhParam, P8 eighthParam) throws Exception;
}
