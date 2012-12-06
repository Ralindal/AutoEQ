package autoeq;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.vecmath.GMatrix;
import javax.vecmath.GVector;

import junit.framework.TestCase;

import com.gregdennis.drej.Kernel;
import com.gregdennis.drej.PolynomialKernel;
import com.gregdennis.drej.Regression;
import com.gregdennis.drej.Representer;

public class TTLTestCase extends TestCase {

  public static void main(String[] args) {
    double[] testData = new double[] {100, 99, 98, 95, 90, 90, 89, 89, 89, 80, 78, 78, 77, 75, 75, 53, 53, 52, 44, 43};

    GMatrix data = new GMatrix(1, testData.length);
    GVector values = new GVector(testData.length);

    for(int i = 0; i < testData.length; i++) {
      data.setColumn(i, new double[] {i});
    }
    values.set(testData);

    Kernel kernel = PolynomialKernel.QUADRATIC_KERNEL;

    Representer representer = Regression.solve(data, values, kernel, 0.1);

    for(int i = 0; i < 50; i++) {
      System.out.printf("%2d -> %5.1f%n", i, representer.eval(new GVector(new double[] {i})));
    }

  }

  public static void main2(@SuppressWarnings("unused") String[] args) {
    @SuppressWarnings("unused")
    int[] testData = new int[] {100, 99, 98, 95, 90, 90, 89, 89, 89, 80, 78, 78, 77, 75, 75, 53, 53, 52, 44, 43};

    GMatrix data = new GMatrix(1, 4);
    GVector values = new GVector(4);

    data.setColumn(0, new double[] {1});
    data.setColumn(1, new double[] {2});
    data.setColumn(2, new double[] {3});
    data.setColumn(3, new double[] {4});

    values.set(new double[] {3, 5, 7, 9});

    Kernel kernel = PolynomialKernel.QUADRATIC_KERNEL;

    Representer representer = Regression.solve(data, values, kernel, 0.5);

    System.out.println(representer.eval(new GVector(new double[] {0})));
    System.out.println(representer.eval(new GVector(new double[] {1})));
    System.out.println(representer.eval(new GVector(new double[] {2})));
    System.out.println(representer.eval(new GVector(new double[] {3})));
    System.out.println(representer.eval(new GVector(new double[] {4})));
    System.out.println(representer.eval(new GVector(new double[] {5})));
    System.out.println(representer.eval(new GVector(new double[] {6})));
  }

  public void test() {
    int[][] testData = new int[][] {
      new int[] {100, 99, 98, 95, 90, 90, 89, 89, 89, 80, 78, 78, 77, 75, 75, 53, 53, 52, 44, 43, 37, 30, 30, 29, 7, 7, 4},
      new int[] {100, 99, 98, 95, 90, 90, 89, 89, 80, 78, 78, 78, 77, 75, 75, 53, 53, 52, 44, 43, 37, 30, 30, 29, 7, 7, 4},
      new int[] {100, 98, 98, 95, 95, 95, 95, 92, 92, 92, 83, 83, 82, 81, 81, 78, 76, 69, 48, 48, 45, 42, 39, 38, 37, 31, 31, 31, 28, 26, 26, 19, 19, 18, 17},
      new int[] {100, 97, 97, 97, 96, 87, 87, 84, 80, 80, 73, 73, 73, 62, 62, 59, 59, 59, 50, 50, 50, 47, 47, 40, 39, 39, 38, 38, 36, 31, 29, 3, 3, 3, 1, 1},
      new int[] {100, 96, 96, 96, 96, 96, 93, 92, 92, 85, 84, 83, 81, 78, 78, 73, 73, 62, 62, 61, 57, 55, 49, 48, 47, 42, 34, 32, 32, 30, 25, 25, 17, 13, 13, 6},
      new int[] {100, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 98, 96, 96, 85, 85, 84, 84, 83, 77, 51, 50, 49, 45, 45, 40, 40, 40, 40, 39, 36, 36, 35, 31, 31, 31, 30, 23, 13, 13, 13, 8, 8},
    };

    for(int[] healths : testData) {
      List<Integer> list = new ArrayList<>();

      for(int i : healths) {
        list.add(i);
      }

      System.out.println(Arrays.toString(smoothData(list, 5)));

      for(int i = 5; i < healths.length; i++) {
        System.out.printf("actualTTL = %2d, calc1 =%3d calc2 =%3d, calc3 =%3d, calc4 =%3d, calc5 =%3d%n", (list.size() - i), calcTTL(list.subList(0, i)), calcTTL2(list.subList(0, i)), calcTTL3(list.subList(0, i)), calcTTL4(list.subList(0, i)), calcTTL5(list.subList(0, i)));
      }
    }
  }

  private static double[] smoothData(List<Integer> data, int factor) {
    double[] smoothedData = new double[data.size() * factor];

    for(int i = 0; i < smoothedData.length; i++) {
      double sampleStart = (double)i / factor - (double)factor / 2;
      double sampledValue = 0;
      int sampleCount = 0;

      for(double s = 0; s < factor * factor; s++) {
        int offset = (int)(sampleStart + s / factor);

        if(offset >= 0 && offset < data.size()) {
          sampledValue += data.get(offset);
          sampleCount++;
        }
      }

      smoothedData[i] = sampledValue / sampleCount;
    }

    return smoothedData;
  }

  public int calcTTL5(List<Integer> secondData) {
    int factor = 5;
    double[] smoothedData = smoothData(secondData, factor);
    int padding = 1;
    int length = smoothedData.length;

    GMatrix data = new GMatrix(1, length + padding);
    GVector values = new GVector(length + padding);

    double[] testData = new double[length + padding];

    for(int i = -padding; i < length; i++) {
      data.setColumn(i + padding, new double[] {i});
      if(i < 0) {
        testData[i + padding] = 100;
      }
      else {
        testData[i + padding] = smoothedData[i];
      }
    }
    values.set(testData);

    Kernel kernel = PolynomialKernel.QUADRATIC_KERNEL;
//    Kernel kernel = LinearKernel.KERNEL;


    Representer representer = Regression.solve(data, values, kernel, 0.1);

    for(int i = length; i < length + 120 * factor; i++) {
      if(representer.eval(new GVector(new double[] {i})) <= 0) {
        return (i - length) / factor;
      }
    }

    return 120;
  }

  public int calcTTL4(List<Integer> secondData) {
    double avgDPS = 0;

    for(int i = 0; i < secondData.size() - 4; i++) {
      double dps4 = (secondData.get(i) - secondData.get(i + 4)) / 4.0;

      avgDPS = avgDPS * 0.7 + dps4 * 0.3;
    }

    int secondsToDeath = (int)(secondData.get(secondData.size() - 1) / avgDPS);

    return secondsToDeath;
  }

  public int calcTTL3(List<Integer> secondData) {
    double currentHealth = secondData.get(secondData.size() - 1);
    double secondsAlive = secondData.size() - 1;

    int secondsToDeath = (int)(currentHealth / ((100 - currentHealth) / secondsAlive));

    return secondsToDeath;
  }

  public int calcTTL2(List<Integer> secondData) {
    double currentHealth = secondData.get(secondData.size() - 1);
    double secondsAlive = secondData.size();

    int secondsToDeath = (int)(currentHealth / ((100 - currentHealth) / secondsAlive));

    return secondsToDeath;
  }

  public int calcTTL(List<Integer> secondData) {
    double avgDPS = 0;
    int previous = 100;

    for(int h : secondData) {
      double dpsLastSecond = previous - h;

      avgDPS = avgDPS * 0.8 + dpsLastSecond * 0.2;

      previous = h;
    }

    int secondsToDeath = (int)(secondData.get(secondData.size() - 1) / avgDPS);

    return secondsToDeath;
  }
}
