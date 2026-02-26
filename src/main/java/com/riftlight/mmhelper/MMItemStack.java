package com.riftlight.mmhelper;

public class MMItemStack {
	private String name;
	private int amt;

	public MMItemStack(String name, int amount) {
		this.name = name;
		this.amt = amount;
	}

	public String getName() {
		return this.name;
	}

	public int getAmount() {
		return this.amt;
	}
}
