/*
 * HIPSTRTreeBuilder.java
 *
 * Copyright © 2002-2024 the BEAST Development Team
 * http://beast.community/about
 *
 * This file is part of BEAST.
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership and licensing.
 *
 * BEAST is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 *  BEAST is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with BEAST; if not, write to the
 * Free Software Foundation, Inc., 51 Franklin St, Fifth Floor,
 * Boston, MA  02110-1301  USA
 *
 */

package dr.app.tools.treeannotator;

import dr.evolution.tree.*;
import dr.evolution.util.Taxon;
import dr.evolution.util.TaxonList;
import dr.util.Pair;

import java.util.HashMap;
import java.util.Map;

public class HIPSTRTreeBuilder {
    static final boolean breakTies = false;

    private final Map<Clade, Double> credibilityCache = new HashMap<>();

    public MutableTree getHIPSTRTree(CladeSystem cladeSystem, TaxonList taxonList, double penaltyThreshold) {
        BiClade rootClade = (BiClade)cladeSystem.getRootClade();

        credibilityCache.clear();

        score = findHIPSTRTree(rootClade, penaltyThreshold);

        // create a map so that tip numbers are in the same order as the taxon list
        Map<Taxon, Integer> taxonNumberMap = new HashMap<>();
        for (int i = 0; i < taxonList.getTaxonCount(); i++) {
            taxonNumberMap.put(taxonList.getTaxon(i), i);
        }

        FlexibleTree tree = new FlexibleTree(buildHIPSTRTree(rootClade), taxonNumberMap);
        tree.setLengthsKnown(false);
        return tree;
    }

    private double findHIPSTRTree(BiClade clade, double penaltyThreshold) {

        double logCredibility = Math.log(clade.getCredibility() + penaltyThreshold);
        if (clade.getCount() == 1) {
            logCredibility = Double.NEGATIVE_INFINITY;
        }
        Map<Pair<BiClade, BiClade>, Double> ties = new HashMap<>();
        if (clade.getSize() > 1) {
            // if it is not a tip
            if (clade.getSize() > 2) {
                // more than two tips in this clade
                double bestLogCredibility = Double.NEGATIVE_INFINITY;

                for (Pair<BiClade, BiClade> subClade : clade.getSubClades()) {
                    BiClade left = subClade.first;

//                    double leftLogCredibility = Math.log(1.0 + penaltyThreshold);
                    double leftLogCredibility = 0.0;
                    if (left.getSize() > 1) {
                        leftLogCredibility = credibilityCache.getOrDefault(left, Double.NaN);
                        if (Double.isNaN(leftLogCredibility)) {
                            leftLogCredibility = findHIPSTRTree(left, penaltyThreshold);
                            credibilityCache.put(left, leftLogCredibility);
                        }
                    }

                    BiClade right = subClade.second;

//                    double rightLogCredibility = Math.log(1.0 + penaltyThreshold);
                    double rightLogCredibility = 0.0;
                    if (right.getSize() > 1) {
                        rightLogCredibility = credibilityCache.getOrDefault(right, Double.NaN);
                        if (Double.isNaN(rightLogCredibility)) {
                            rightLogCredibility = findHIPSTRTree(right, penaltyThreshold);
                            credibilityCache.put(right, rightLogCredibility);
                        }
                    }

                    if (breakTies) {
                        if (leftLogCredibility + rightLogCredibility >= bestLogCredibility) {
                            if (leftLogCredibility + rightLogCredibility > bestLogCredibility) {
                                ties.clear();
                                bestLogCredibility = leftLogCredibility + rightLogCredibility;
                                if (left.getSize() < right.getSize()) {
                                    // sort by clade size because why not...
                                    clade.bestLeft = left;
                                    clade.bestRight = right;
                                } else {
                                    clade.bestLeft = right;
                                    clade.bestRight = left;
                                }
//                            ties.put(new Pair<>(left, right), Math.min(left.getCredibility(), right.getCredibility()));
                                ties.put(new Pair<>(left, right), (left.getCredibility() + 0.5) * (right.getCredibility() + 0.5));
                            }
                        }
                    } else {
                        if (leftLogCredibility + rightLogCredibility > bestLogCredibility) {
                            bestLogCredibility = leftLogCredibility + rightLogCredibility;
                            if (left.getSize() < right.getSize()) {
                                // sort by clade size because why not...
                                clade.bestLeft = left;
                                clade.bestRight = right;
                            } else {
                                clade.bestLeft = right;
                                clade.bestRight = left;
                            }
                        }
                    }

                    if (breakTies && ties.size() > 1) {
                        double best = Double.NEGATIVE_INFINITY;
                        for (Pair<BiClade, BiClade> key : ties.keySet()) {
                            double value = ties.get(key);
                            if (value > best) {
                                clade.bestLeft = key.first;
                                clade.bestRight = key.second;
                                best = value;
                            }
                        }
                    }
                }

                logCredibility += bestLogCredibility;
            } else {
                // two tips so there will only be one pair and their sum log cred will be 0.0
                assert clade.getSubClades().size() == 1;
                Pair<BiClade, BiClade> subClade = clade.getSubClades().stream().findFirst().get();
                clade.bestLeft = subClade.first;
                clade.bestRight = subClade.second;

                if (clade.bestLeft.getTaxon().getId().contains("EPI_ISL_477006")) {
                    System.out.println("EPI_ISL_477006");
                }
                // logCredibility += 0.0;
            }

            clade.bestSubTreeCredibility = logCredibility;
        }

        return logCredibility;
    }

    private FlexibleNode buildHIPSTRTree(Clade clade) {
        FlexibleNode newNode = new FlexibleNode();
        if (clade.getSize() == 1) {
            newNode.setTaxon(clade.getTaxon());
            newNode.setNumber(clade.getIndex());
        } else {
            newNode.addChild(buildHIPSTRTree(clade.getBestLeft()));
            newNode.addChild(buildHIPSTRTree(clade.getBestRight()));
            newNode.setNumber(-1);
        }
        return newNode;
    }

    public double getScore() {
        return score;
    }

    private double score = Double.NaN;
}
