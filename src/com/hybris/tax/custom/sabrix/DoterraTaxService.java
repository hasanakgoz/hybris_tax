/*
 * [y] hybris Platform
 *
 * Copyright (c) 2000-2016 SAP SE
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of SAP
 * Hybris ("Confidential Information"). You shall not disclose such
 * Confidential Information and shall use it only in accordance with the
 * terms of the license agreement you entered into with SAP Hybris.
 */
package com.hybris.tax.custom.sabrix;

import de.hybris.platform.core.model.order.AbstractOrderModel;
import java.util.Map;
import com.hybris.doterra.process.tax.sabrix.services.taxcalculationservice.OutdataInvoiceType;
import de.hybris.platform.core.model.user.UserGroupModel;


/**
 *
 */
public interface DoterraTaxService
{

	public OutdataInvoiceType taxCalculation(AbstractOrderModel abstractOrder, Map<Integer, Map<String, String>> priceDetails,UserGroupModel usrGroup)
	                  throws Exception;

   public boolean checkServiceConnection();
}
