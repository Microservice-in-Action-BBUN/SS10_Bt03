package com.rikkeipay.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO đại diện cho kết quả phân tích ý định chuyển khoản từ Prompt Registry.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TransferIntentResponse {

    @JsonProperty("action")
    private String action; // TRANSFER, ASK_CLARIFICATION, REJECT

    @JsonProperty("status")
    private String status; // VALID, INSUFFICIENT_FUNDS, INVALID_INPUT, FRAUD_ALERT, OUT_OF_SCOPE

    @JsonProperty("transfer_details")
    private TransferDetails transferDetails;

    @JsonProperty("validation_error")
    private String validationError;

    @JsonProperty("response_message")
    private String responseMessage;

    public TransferIntentResponse() {
    }

    public static class TransferDetails {
        @JsonProperty("to_account")
        private String toAccount;

        @JsonProperty("recipient_name")
        private String recipientName;

        @JsonProperty("amount")
        private Double amount;

        @JsonProperty("bank_code")
        private String bankCode;

        @JsonProperty("transfer_message")
        private String transferMessage;

        public String getToAccount() { return toAccount; }
        public void setToAccount(String toAccount) { this.toAccount = toAccount; }

        public String getRecipientName() { return recipientName; }
        public void setRecipientName(String recipientName) { this.recipientName = recipientName; }

        public Double getAmount() { return amount; }
        public void setAmount(Double amount) { this.amount = amount; }

        public String getBankCode() { return bankCode; }
        public void setBankCode(String bankCode) { this.bankCode = bankCode; }

        public String getTransferMessage() { return transferMessage; }
        public void setTransferMessage(String transferMessage) { this.transferMessage = transferMessage; }
    }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public TransferDetails getTransferDetails() { return transferDetails; }
    public void setTransferDetails(TransferDetails transferDetails) { this.transferDetails = transferDetails; }

    public String getValidationError() { return validationError; }
    public void setValidationError(String validationError) { this.validationError = validationError; }

    public String getResponseMessage() { return responseMessage; }
    public void setResponseMessage(String responseMessage) { this.responseMessage = responseMessage; }
}
