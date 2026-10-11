package app.moneymanager.ui.common

import app.moneymanager.core.analysis.TxnRecord
import app.moneymanager.core.model.TxnType
import app.moneymanager.data.db.TransactionEntity

fun TransactionEntity.toRecord() = TxnRecord(
    type = if (type == "CREDIT") TxnType.CREDIT else TxnType.DEBIT,
    amountPaise = amountPaise,
    timestamp = timestamp,
    categoryKey = categoryKey,
    merchant = merchant ?: vpa,
    merchantKey = merchantKey,
    excluded = excluded,
)

val TransactionEntity.isCredit get() = type == "CREDIT"
