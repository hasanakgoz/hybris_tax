/*
 * [y] hybris Platform
 *
 * Copyright (c) 2000-2017 SAP SE
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of SAP
 * Hybris ("Confidential Information"). You shall not disclose such
 * Confidential Information and shall use it only in accordance with the
 * terms of the license agreement you entered into with SAP Hybris.
 */
package com.hybris.tax.custom.core;

import javax.xml.namespace.QName;
/**
 *
 */
import javax.xml.ws.Service;

import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;

import com.hybris.doterra.process.tax.sabrix.services.taxcalculationservice.TaxCalculationService;


public class CustomPoolFactory extends BasePooledObjectFactory<ProxyWrapper<TaxCalculationService>>
{
	private Service service = null;
	private static final QName qname = new QName("http://www.sabrix.com/services/taxcalculationservice/2011-09-01",
			"TaxCalculationServicePort");

	public CustomPoolFactory(final Service service)
	{
		this.service = service;
	}

	@Override
	public synchronized ProxyWrapper<TaxCalculationService> create()
	{
		final ProxyWrapper<TaxCalculationService> h = new ProxyWrapper<TaxCalculationService>(
				service.getPort(qname, TaxCalculationService.class));
		return h;
	}

	@Override
	public PooledObject<ProxyWrapper<TaxCalculationService>> wrap(final ProxyWrapper<TaxCalculationService> obj)
	{
		return new DefaultPooledObject<ProxyWrapper<TaxCalculationService>>(obj);
	}
}