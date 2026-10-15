package skillforge.domain.cohort;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

public final class GradeAverage {

    private GradeAverage() {
    }

    public static OptionalInt tenths(List<Integer> practices, List<Integer> exams, int practiceWeight, int examWeight) {
        List<Integer> practiceGrades = practices == null ? List.of() : practices;
        List<Integer> examGrades = exams == null ? List.of() : exams;
        if (practiceGrades.isEmpty() && examGrades.isEmpty()) {
            return OptionalInt.empty();
        }
        if (practiceWeight == 1 && examWeight == 1) {
            List<Integer> grades = new ArrayList<>();
            grades.addAll(practiceGrades);
            grades.addAll(examGrades);
            return OptionalInt.of(meanTenths(grades));
        }
        if (practiceGrades.isEmpty()) {
            return OptionalInt.of(meanTenths(examGrades));
        }
        if (examGrades.isEmpty()) {
            return OptionalInt.of(meanTenths(practiceGrades));
        }
        double mixed = (mean(practiceGrades) * practiceWeight + mean(examGrades) * examWeight)
                / (practiceWeight + examWeight);
        return OptionalInt.of((int) Math.round(mixed * 10));
    }

    public static String format(int tenths) {
        return tenths / 10 + "." + Math.floorMod(tenths, 10);
    }

    public static boolean presentEnough(int days, int present) {
        return days <= 0 || present * 100 >= days * 75;
    }

    public static boolean earnsCertificate(int tenths, int days, int present) {
        return tenths >= 70 && presentEnough(days, present);
    }

    private static int meanTenths(List<Integer> grades) {
        if (grades.isEmpty()) {
            return 0;
        }
        return (int) Math.round(mean(grades) * 10);
    }

    private static double mean(List<Integer> grades) {
        int sum = 0;
        for (int grade : grades) {
            sum += grade;
        }
        return sum / (double) grades.size();
    }

}
