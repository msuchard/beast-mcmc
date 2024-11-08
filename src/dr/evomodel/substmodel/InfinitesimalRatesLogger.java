/*
 * InfinitesimalRatesLogger.java
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

package dr.evomodel.substmodel;

import dr.inference.loggers.LogColumn;
import dr.inference.loggers.Loggable;
import dr.inference.loggers.NumberColumn;
import dr.util.Transform;

public class InfinitesimalRatesLogger implements Loggable {

    public InfinitesimalRatesLogger(SubstitutionModel substitutionModel, boolean diagonalElements, Transform transform) {
        this.substitutionModel = substitutionModel;
        this.diagonalElements = diagonalElements;
        this.transform = transform;
    }



    @Override
    public LogColumn[] getColumns() {
        int stateCount = substitutionModel.getDataType().getStateCount();

        if (generator == null) {
            generator = new double[stateCount * stateCount];
        }

        int nOutputs = stateCount * stateCount;
        if (!diagonalElements) nOutputs -= stateCount;
        LogColumn[] columns = new LogColumn[nOutputs];

        int index = 0;
        for (int i = 0; i < stateCount; ++i) {
            for (int j = 0; j < stateCount; ++j) {
                if (!diagonalElements && i == j) {
                    continue;
                }
                final int row = i;
                final int col = j;
                final int k = index++;  // Use index to store column number
                columns[k] = new NumberColumn(substitutionModel.getId() + "." + (i + 1) + "." + (j + 1)) {
                    @Override
                    public double getDoubleValue() {
                        if (k == 0) { // Refresh at first-element read
                            substitutionModel.getInfinitesimalMatrix(generator);
                        }
                        if (transform != null) {
                            return transform.transform(generator[row * stateCount + col]);
                        } else {
                            return generator[row * stateCount + col];
                        }
                    }
                };
            }
        }

//        for (int i = 0; i < stateCount; ++i) {
//            for (int j = 0; j < stateCount; ++j) {
//                final int k = i * stateCount + j;
//                columns[k] = new NumberColumn(substitutionModel.getId() + "." + (i + 1) + "." + (j + 1)) {
//                    @Override
//                    public double getDoubleValue() {
//                        if (k == 0) { // Refresh at first-element read
//                            substitutionModel.getInfinitesimalMatrix(generator);
//                        }
//                        return generator[k];
//                    }
//                };
//            }
//        }

        return columns;
    }
    private final Transform transform;
    private final SubstitutionModel substitutionModel;
    private final Boolean diagonalElements;
    private double[] generator;
}
