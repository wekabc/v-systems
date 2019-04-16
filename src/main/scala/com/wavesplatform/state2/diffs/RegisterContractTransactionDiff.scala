package com.wavesplatform.state2.diffs

import com.wavesplatform.state2.reader.StateReader
import com.wavesplatform.state2.{Diff, LeaseInfo, Portfolio}
import scorex.transaction.ValidationError
import scorex.transaction.ValidationError.GenericError
import vsys.contract.ExecutionContext
import vsys.state.opcdiffs.OpcFuncDiffer
import vsys.transaction.contract.RegisterContractTransaction
import vsys.transaction.TransactionStatus
import vsys.transaction.proof.{EllipticCurve25519Proof, Proofs}

import scala.util.Left

object RegisterContractTransactionDiff {
  def apply(s: StateReader, height: Int)(tx: RegisterContractTransaction): Either[ValidationError, Diff] = {
    //no need to validate the name duplication coz that will create a duplicate transacion and
    // will fail with duplicated transaction id
    if (tx.proofs.proofs.length > Proofs.MaxProofs){
      Left(GenericError(s"Too many proofs, max ${Proofs.MaxProofs} proofs"))
    }
    else {
      val sender = EllipticCurve25519Proof.fromBytes(tx.proofs.proofs.head.bytes.arr).toOption.get.publicKey
      val contractInfo = (height, tx.id, tx.contract, Set(sender.toAddress))
      (for {
        exContext <- ExecutionContext.fromRegConTx(s, height, tx)
        diff <- OpcFuncDiffer(exContext)(tx.data)
      } yield diff) match {
        case Left(_) => Right(Diff(height = height, tx = tx,
          portfolios = Map(sender.toAddress -> Portfolio(-tx.fee, LeaseInfo.empty, Map.empty)),
          chargedFee = tx.fee, txStatus = TransactionStatus.RegisterContractFailed))
        case Right(df) => Right(Diff(height = height, tx = tx,
          portfolios = Map(sender.toAddress -> Portfolio(-tx.fee, LeaseInfo.empty, Map.empty)),
          contracts = Map(tx.contractId.bytes -> contractInfo),
          contractDB = df.contractDB, contractTokens = df.contractTokens,
          tokenDB = df.tokenDB, tokenAccountBalance = df.tokenAccountBalance, relatedAddress = df.relatedAddress,
          chargedFee = tx.fee))
      }
    }
  }

}