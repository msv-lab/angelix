package sg.edu.nus.comp.nsynth;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import sg.edu.nus.comp.nsynth.ast.*;
import sg.edu.nus.comp.nsynth.ast.theory.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Sergey Mechtaev on 7/4/2016.
 */
public class Cardinality {

    public static class Pairwise {
        static List<Node> atMostOne(List<? extends Variable> bits) {
            ArrayList<Node> clauses = new ArrayList<>();
            for (Variable bit1 : bits) {
                for (Variable bit2 : bits) {
                    if (!bit1.equals(bit2)) {
                        clauses.add(new Or(new Not(bit1), new Not(bit2)));
                    }
                }
            }
            return clauses;
        }
    }

    /**
     * A Parametric Approach for Smaller and Better Encodings of Cardinality Constraints
     * by Ignasi Abio, Robert Nieuwenhuis, Albert Oliveras, Enric Rodriguez-Carbonell
     */
    public static class SortingNetwork {

        private static Pair<List<? extends Variable>, List<? extends Variable>> splitByEvenness(List<? extends Variable> list) {
            List<Variable> odd = new ArrayList<>();
            List<Variable> even = new ArrayList<>();

            boolean currentIsOdd = true;
            for (Variable variable : list) {
                if (currentIsOdd) {
                    odd.add(variable);
                } else {
                    even.add(variable);
                }
                currentIsOdd = !currentIsOdd;
            }
            return new ImmutablePair<>(odd, even);
        }

        private static Pair<List<Node>, List<? extends Variable>> twoComp(Variable x1, Variable x2) {
            Selector y1 = new Selector();
            Selector y2 = new Selector();
            List<Selector> sorted = new ArrayList<>();
            sorted.add(y1);
            sorted.add(y2);

            List<Node> algorithm = new ArrayList<>();
            algorithm.add(new Impl(x1, y1));
            algorithm.add(new Impl(x2, y1));
            algorithm.add(new Impl(new And(x1, x2), y2));

            return new ImmutablePair<>(algorithm, sorted);
        }

        private static Pair<List<Node>, List<? extends Variable>> pairwiseComp(List<? extends Variable> left,
                                                                               List<? extends Variable> right) {
            List<Node> algorithm = new ArrayList<>();
            List<Variable> sorted = new ArrayList<>();

            assert (left.size() == right.size());
            int size = left.size();

            for (int i=0; i<size; i++) {
                Pair<List<Node>, List<? extends Variable>> twoCompResult = twoComp(left.get(i), right.get(i));
                algorithm.addAll(twoCompResult.getLeft());
                sorted.addAll(twoCompResult.getRight());
            }

            return new ImmutablePair<>(algorithm, sorted);
        }

        private static Pair<List<Node>, List<? extends Variable>> merge(List<? extends Variable> left,
                                                                        List<? extends Variable> right) {
            int a = left.size();
            int b = right.size();

            if (a == 0) {
                return new ImmutablePair<>(new ArrayList<>(), right);
            }

            if (b == 0) {
                return new ImmutablePair<>(new ArrayList<>(), left);
            }

            if (a == 1 && b == 1) {
                return twoComp(left.get(0), right.get(0));
            }

            if (a % 2 == 1 && b % 2 == 0) {
                return merge(right, left);
            }

            List<Node> algorithm = new ArrayList<>();
            List<Variable> sorted = new ArrayList<>();

            Pair<List<? extends Variable>, List<? extends Variable>> splitLeft = splitByEvenness(left);
            Pair<List<? extends Variable>, List<? extends Variable>> splitRight = splitByEvenness(right);
            List<? extends Variable> leftOdd = splitLeft.getLeft();
            List<? extends Variable> leftEven = splitLeft.getRight();
            List<? extends Variable> rightOdd = splitRight.getLeft();
            List<? extends Variable> rightEven = splitRight.getRight();

            Pair<List<Node>, List<? extends Variable>> oddResult = merge(leftOdd, rightOdd);
            Pair<List<Node>, List<? extends Variable>> evenResult = merge(leftEven, rightEven);
            algorithm.addAll(oddResult.getLeft());
            algorithm.addAll(evenResult.getLeft());
            List<? extends Variable> oddResultVariables = oddResult.getRight();
            List<? extends Variable> evenResultVariables = evenResult.getRight();

            if (a % 2 == 0 && b % 2 == 0) {
                Variable z1 = oddResultVariables.get(0);
                List<? extends Variable> restOfOdds = oddResultVariables.subList(1, oddResultVariables.size());
                Variable zLast = evenResultVariables.get(evenResultVariables.size() - 1);
                List<? extends Variable> restOfEvens = evenResultVariables.subList(0, evenResultVariables.size() - 1);
                Pair<List<Node>, List<? extends Variable>> pairwiseResult = pairwiseComp(restOfEvens, restOfOdds);
                algorithm.addAll(pairwiseResult.getLeft());
                List<? extends Variable> pairwiseResultVariables = pairwiseResult.getRight();
                sorted.add(z1);
                sorted.addAll(pairwiseResultVariables);
                sorted.add(zLast);
            }

            if (a % 2 == 1 && b % 2 == 1) {
                Variable z1 = oddResultVariables.get(0);
                Variable zLast = oddResultVariables.get(oddResultVariables.size() - 1);
                List<? extends Variable> restOfOdds = oddResultVariables.subList(1, oddResultVariables.size() - 1);
                Pair<List<Node>, List<? extends Variable>> pairwiseResult = pairwiseComp(evenResultVariables, restOfOdds);
                algorithm.addAll(pairwiseResult.getLeft());
                List<? extends Variable> pairwiseResultVariables = pairwiseResult.getRight();
                sorted.add(z1);
                sorted.addAll(pairwiseResultVariables);
                sorted.add(zLast);
            }

            if (a % 2 == 0 && b % 2 == 1) {
                Variable z1 = oddResultVariables.get(0);
                List<? extends Variable> restOfOdds = oddResultVariables.subList(1, oddResultVariables.size());
                Pair<List<Node>, List<? extends Variable>> pairwiseResult = pairwiseComp(evenResultVariables, restOfOdds);
                algorithm.addAll(pairwiseResult.getLeft());
                List<? extends Variable> pairwiseResultVariables = pairwiseResult.getRight();
                sorted.add(z1);
                sorted.addAll(pairwiseResultVariables);
            }

            return new ImmutablePair<>(algorithm, sorted);
        }

        private static Pair<List<Node>, List<? extends Variable>> makeSortingNetwork(List<? extends Variable> bits) {
            int n = bits.size();
            if (n == 1) {
                return new ImmutablePair<>(new ArrayList<>(), bits);
            }
            if (n == 2) {
                return twoComp(bits.get(0), bits.get(1));
            }
            List<Node> algorithm = new ArrayList<>();
            List<Variable> sorted = new ArrayList<>();

            int l = n/2;
            List<? extends Variable> left = bits.subList(0, l);
            List<? extends Variable> right = bits.subList(l, n);
            Pair<List<Node>, List<? extends Variable>> leftNetwork = makeSortingNetwork(left);
            Pair<List<Node>, List<? extends Variable>> rightNetwork = makeSortingNetwork(right);
            algorithm.addAll(leftNetwork.getLeft());
            algorithm.addAll(rightNetwork.getLeft());
            Pair<List<Node>, List<? extends Variable>> mergeResult = merge(leftNetwork.getRight(), rightNetwork.getRight());
            algorithm.addAll(mergeResult.getLeft());
            sorted.addAll(mergeResult.getRight());

            return new ImmutablePair<>(algorithm, sorted);
        }

        public static List<Node> atMostK(int k, List<? extends Variable> bits) {
            ArrayList<Node> result = new ArrayList<>();
            if (k >= bits.size()) {
                return result;
            }
            Pair<List<Node>, List<? extends Variable>> sortingNetwork = makeSortingNetwork(bits);
            result.addAll(sortingNetwork.getLeft());
            result.add(new Not(sortingNetwork.getRight().get(k)));
            return result;
        }

        public static List<Node> atLeastK(int k, List<? extends Variable> bits) {
            throw new UnsupportedOperationException();
        }
    }


    public static class ArithmeticCircuit {
        private static Pair<List<Node>, List<Selector>> makeArithmeticCircuit(List<? extends Variable> bits) {
            throw new UnsupportedOperationException();
        }

        public static List<Node> atMostK(int k, List<? extends Variable> bits) {
            throw new UnsupportedOperationException();
        }

        public static List<Node> atLeastK(int k, List<? extends Variable> bits) {
            throw new UnsupportedOperationException();
        }
    }
}
