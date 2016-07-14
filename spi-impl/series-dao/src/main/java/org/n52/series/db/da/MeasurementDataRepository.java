/*
 * Copyright (C) 2013-2016 52°North Initiative for Geospatial Open Source
 * Software GmbH
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 as published
 * by the Free Software Foundation.
 *
 * If the program is linked with libraries which are licensed under one of
 * the following licenses, the combination of the program with the linked
 * library is not considered a "derivative work" of the program:
 *
 *     - Apache License, version 2.0
 *     - Apache Software License, version 1.0
 *     - GNU Lesser General Public License, version 3
 *     - Mozilla Public License, versions 1.0, 1.1 and 2.0
 *     - Common Development and Distribution License (CDDL), version 1.0
 *
 * Therefore the distribution of the program linked with libraries licensed
 * under the aforementioned licenses, is permitted by the copyright holders
 * if the distribution is compliant with both the GNU General Public License
 * version 2 and the aforementioned licenses.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * for more details.
 */

package org.n52.series.db.da;

import static java.math.RoundingMode.HALF_UP;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.hibernate.Session;
import org.joda.time.Interval;
import org.n52.io.response.series.MeasurementData;
import org.n52.io.response.series.MeasurementDataMetadata;
import org.n52.io.response.series.MeasurementValue;
import org.n52.io.response.v1.ext.ObservationType;
import org.n52.series.db.DataAccessException;
import org.n52.series.db.beans.MeasurementDataEntity;
import org.n52.series.db.beans.MeasurementDatasetEntity;
import org.n52.series.db.dao.DbQuery;
import org.n52.series.db.dao.ObservationDao;
import org.n52.series.db.dao.SeriesDao;

public class MeasurementDataRepository extends AbstractDataRepository<MeasurementData, MeasurementDatasetEntity> {

    @Override
    public Class<MeasurementDatasetEntity> getEntityType() {
        return MeasurementDatasetEntity.class;
    }

    @Override
    public MeasurementData getData(String seriesId, DbQuery dbQuery) throws DataAccessException {
        Session session = getSession();
        try {
            SeriesDao<MeasurementDatasetEntity> seriesDao = new SeriesDao<>(session, MeasurementDatasetEntity.class);
            String id = ObservationType.extractId(seriesId);
            MeasurementDatasetEntity series = seriesDao.getInstance(parseId(id), dbQuery);
            return dbQuery.isExpanded()
                ? assembleDataWithReferenceValues(series, dbQuery, session)
                : assembleData(series, dbQuery, session);
        }
        finally {
            returnSession(session);
        }
    }

    private MeasurementData assembleDataWithReferenceValues(MeasurementDatasetEntity timeseries,
                                                            DbQuery dbQuery,
                                                            Session session) throws DataAccessException {
        MeasurementData result = assembleData(timeseries, dbQuery, session);
        Set<MeasurementDatasetEntity> referenceValues = timeseries.getReferenceValues();
        if (referenceValues != null && !referenceValues.isEmpty()) {
            MeasurementDataMetadata metadata = new MeasurementDataMetadata();
            metadata.setReferenceValues(assembleReferenceSeries(referenceValues, dbQuery, session));
            result.setMetadata(metadata);
        }
        return result;
    }

    private Map<String, MeasurementData> assembleReferenceSeries(Set<MeasurementDatasetEntity> referenceValues,
                                                                 DbQuery query,
                                                                 Session session) throws DataAccessException {
        Map<String, MeasurementData> referenceSeries = new HashMap<>();
        for (MeasurementDatasetEntity referenceSeriesEntity : referenceValues) {
            if (referenceSeriesEntity.isPublished()) {
                MeasurementData referenceSeriesData = assembleData(referenceSeriesEntity, query, session);
                if (haveToExpandReferenceData(referenceSeriesData)) {
                    referenceSeriesData = expandReferenceDataIfNecessary(referenceSeriesEntity, query, session);
                }
                referenceSeries.put(referenceSeriesEntity.getPkid().toString(), referenceSeriesData);
            }
        }
        return referenceSeries;
    }

    private boolean haveToExpandReferenceData(MeasurementData referenceSeriesData) {
        return referenceSeriesData.getValues().length <= 1;
    }

    private MeasurementData expandReferenceDataIfNecessary(MeasurementDatasetEntity seriesEntity, DbQuery query, Session session) throws DataAccessException {
        MeasurementData result = new MeasurementData();
        ObservationDao<MeasurementDataEntity> dao = new ObservationDao<>(session);
        List<MeasurementDataEntity> observations = dao.getAllInstancesFor(seriesEntity, query);
        if (!hasValidEntriesWithinRequestedTimespan(observations)) {
            MeasurementDataEntity lastValidEntity = seriesEntity.getLastValue();
            result.addValues(expandToInterval(query.getTimespan(), lastValidEntity, seriesEntity));
        }

        if (hasSingleValidReferenceValue(observations)) {
            MeasurementDataEntity entity = observations.get(0);
            result.addValues(expandToInterval(query.getTimespan(), entity, seriesEntity));
        }
        return result;
    }

    private MeasurementData assembleData(MeasurementDatasetEntity seriesEntity, DbQuery query, Session session) throws DataAccessException {
        MeasurementData result = new MeasurementData();
        ObservationDao<MeasurementDataEntity> dao = new ObservationDao<>(session);
        List<MeasurementDataEntity> observations = dao.getAllInstancesFor(seriesEntity, query);
        for (MeasurementDataEntity observation : observations) {
            if (observation != null) {
                result.addValues(createSeriesValueFor(observation, seriesEntity));
            }
        }
        return result;
    }

    private MeasurementValue[] expandToInterval(Interval interval, MeasurementDataEntity entity, MeasurementDatasetEntity series) {
        MeasurementDataEntity referenceStart = new MeasurementDataEntity();
        MeasurementDataEntity referenceEnd = new MeasurementDataEntity();
        referenceStart.setTimestamp(interval.getStart().toDate());
        referenceEnd.setTimestamp(interval.getEnd().toDate());
        referenceStart.setValue(entity.getValue());
        referenceEnd.setValue(entity.getValue());
        return new MeasurementValue[]{createSeriesValueFor(referenceStart, series),
            createSeriesValueFor(referenceEnd, series)};

    }

    MeasurementValue createSeriesValueFor(MeasurementDataEntity observation, MeasurementDatasetEntity series) {
        if (observation == null) {
            // do not fail on empty observations
            return null;
        }
        MeasurementValue value = new MeasurementValue();
        value.setTimestamp(observation.getTimestamp().getTime());
        Double observationValue = !getServiceInfo().isNoDataValue(observation)
                ? formatDecimal(observation.getValue(), series)
                : Double.NaN;
        value.setValue(observationValue);
        addGeometry(observation, value);
        addValidTime(observation, value);
        addParameter(observation, value);
        return value;
    }

    private Double formatDecimal(Double value, MeasurementDatasetEntity series) {
        int scale = series.getNumberOfDecimals();
        return new BigDecimal(value)
                .setScale(scale, HALF_UP)
                .doubleValue();
    }

}
