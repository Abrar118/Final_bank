package io.github.abrar118.matbank.domain;

/** What a ledger line represents. Amounts are signed: credits positive, debits negative. */
public enum TxKind {
    OPENING_BALANCE("Opening balance", Category.INCOMING),
    DEPOSIT("Deposit", Category.DEPOSIT),
    TRANSFER_IN("Transfer received", Category.INCOMING),
    TRANSFER_OUT("Transfer sent", Category.OUTGOING),
    INTERNAL_IN("From own account", Category.INTERNAL),
    INTERNAL_OUT("To own account", Category.INTERNAL),
    FEE("Service charge", Category.FEE);

    /** Coarse grouping used by the history filter. */
    public enum Category {
        INCOMING("Money in"),
        OUTGOING("Money out"),
        DEPOSIT("Deposits"),
        INTERNAL("Between my accounts"),
        FEE("Charges");

        private final String label;

        Category(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private final String label;
    private final Category category;

    TxKind(String label, Category category) {
        this.label = label;
        this.category = category;
    }

    public String label() {
        return label;
    }

    public Category category() {
        return category;
    }
}
