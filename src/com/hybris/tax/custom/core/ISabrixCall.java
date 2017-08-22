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

import de.hybris.platform.core.model.order.AbstractOrderModel;

import com.hybris.doterra.process.tax.sabrix.services.taxcalculationservice.OutdataInvoiceType;


/**
 *
 */
public interface ISabrixCall
{
	public OutdataInvoiceType taxCalculation(final AbstractOrderModel cart);
}
