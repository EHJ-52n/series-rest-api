/**
 * Copyright (C) 2013-2015 52°North Initiative for Geospatial Open Source
 * Software GmbH
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License version 2 as publishedby the Free
 * Software Foundation.
 *
 * If the program is linked with libraries which are licensed under one of the
 * following licenses, the combination of the program with the linked library is
 * not considered a "derivative work" of the program:
 *
 *     - Apache License, version 2.0
 *     - Apache Software License, version 1.0
 *     - GNU Lesser General Public License, version 3
 *     - Mozilla Public License, versions 1.0, 1.1 and 2.0
 *     - Common Development and Distribution License (CDDL), version 1.0
 *
 * Therefore the distribution of the program linked with libraries licensed under
 * the aforementioned licenses, is permitted by the copyright holders if the
 * distribution is compliant with both the GNU General Public License version 2
 * and the aforementioned licenses.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 */
package org.n52.series.db.srv.v2;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.n52.sensorweb.spi.SearchResult;
import org.n52.sensorweb.spi.SearchService;
import org.n52.series.db.da.v2.CategoryRepository;
import org.n52.series.db.da.v2.FeatureRepository;
import org.n52.series.db.da.v2.PhenomenonRepository;
import org.n52.series.db.da.v2.PlatformRepository;
import org.n52.series.db.da.v2.ProcedureRepository;
import org.n52.series.db.srv.ServiceInfoAccess;

public class Search extends ServiceInfoAccess implements SearchService {

    @Override
    public Collection<SearchResult> searchResources(String search, String locale) {
        List<SearchResult> results = new ArrayList<SearchResult>();
//        results.addAll(getTimeseriesRepository().searchFor(search, locale));
        results.addAll(getPhenomenonRepository().searchFor(search, locale));
        results.addAll(getProcedureRepository().searchFor(search, locale));
        results.addAll(getPlatformRepository().searchFor(search, locale));
        results.addAll(getFeatureRepository().searchFor(search, locale));
        results.addAll(getCategoryRepositry().searchFor(search, locale));
        return results;
    }


    private ProcedureRepository getProcedureRepository() {
        return new ProcedureRepository(getServiceInfo());
    }

    private PhenomenonRepository getPhenomenonRepository() {
        return new PhenomenonRepository(getServiceInfo());
    }

    private PlatformRepository getPlatformRepository() {
		return new PlatformRepository(getServiceInfo());
	}

	private FeatureRepository getFeatureRepository() {
		return new FeatureRepository(getServiceInfo());
	}

	private CategoryRepository getCategoryRepositry() {
        return new CategoryRepository(getServiceInfo());
    }

}