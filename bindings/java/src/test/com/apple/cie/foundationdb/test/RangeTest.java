/*
 * RangeTest.java
 *
 * This source file is part of the FoundationDB open source project
 *
 * Copyright 2013-2018 Apple Inc. and the FoundationDB project authors
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.apple.cie.foundationdb.test;

import java.util.List;

import com.apple.cie.foundationdb.Database;
import com.apple.cie.foundationdb.FDB;
import com.apple.cie.foundationdb.FDBException;
import com.apple.cie.foundationdb.KeySelector;
import com.apple.cie.foundationdb.KeyValue;
import com.apple.cie.foundationdb.Range;
import com.apple.cie.foundationdb.Transaction;
import com.apple.cie.foundationdb.async.AsyncIterable;
import com.apple.cie.foundationdb.async.Function;

public class RangeTest {
	private static final int API_VERSION = 500;

	public static void main(String[] args) throws Exception {
		System.out.println("About to use version " + API_VERSION);
		FDB fdb = FDB.selectAPIVersion(API_VERSION);

		/*
		final String CLUSTER_FILE = "T:\\Ben\\cluster";
		String clusterFile = CLUSTER_FILE;
		if(args.length > 0) {
			clusterFile = args[0];
		}

		System.out.println("Using cluster file: " + clusterFile);
		Cluster cluster = fdb.createCluster(clusterFile).get();
		Database db = cluster.openDatabase().get();
		*/

		Database db = fdb.open();

		try {
			db.run(new Function<Transaction, Void>() {
				@Override
				public Void apply(Transaction tr) {
					long version = tr.getReadVersion().get();
					System.out.println("DB version: " + version);
					tr.get("apple1".getBytes()).get();
					tr.set("apple1".getBytes(), "crunchy1".getBytes());
					tr.set("apple2".getBytes(), "crunchy2".getBytes());
					tr.set("apple3".getBytes(), "crunchy3".getBytes());
					tr.set("apple4".getBytes(), "crunchy4".getBytes());
					tr.set("apple5".getBytes(), "crunchy5".getBytes());
					tr.set("apple6".getBytes(), "crunchy6".getBytes());
					System.out.println("Attempting to commit apple/crunchy pairs...");
					return null;
				}
			});
		} catch (Throwable e){
			e.printStackTrace();
			System.out.println("Non retryable exception caught...");
		}

		System.out.println("First transaction was successful");

		checkRange(db.createTransaction());

		Transaction tr = db.createTransaction();
		long version = tr.getReadVersion().get();
		System.out.println("DB version: " + version);

		byte[] bs = tr.get("apple3".getBytes()).get();
		System.out.println("Got apple3: " + new String(bs));

		tr.cancel();
		try {
			tr.get("apple3".getBytes()).get();
			throw new RuntimeException("The get() should have thrown an error!");
		} catch(FDBException e) {
			if(e.getCode() != 1025) {
				System.err.println("Transaction was not cancelled correctly (" + e.getCode() + ")");
				throw e;
			}
			System.out.println("Transaction was cancelled correctly");
		}

		tr = tr.reset();
		version = tr.getReadVersion().get();
		System.out.println("DB version: " + version);

		tr.clear("apple3".getBytes(), "apple6".getBytes());
		try {
			tr.commit().get();
			System.out.println("Clear range transaction was successful");
		} catch(FDBException e) {
			System.err.println("Error in the clear of a single value");
			e.printStackTrace();
			return;
		}
		//db.dispose();
		//cluster.dispose();

		tr = db.createTransaction();
		checkRange(tr);

		Range r1 = new Range("apple".getBytes(), "banana".getBytes());
		Range r2 = new Range("apple".getBytes(), "banana".getBytes());
		Range r3 = new Range("apple".getBytes(), "crepe".getBytes());
		Range r4 = new Range(null, "banana".getBytes());
		Range r5 = new Range(new byte[]{0x15, 0x01}, null);

		System.out.println("ranges: " + r1 + ", " + r2 + ", " + r3 + ", " + r4 + ", " + r5);

		if(r1.equals(null)) {
			System.err.println("range " + r1 + " equals null");
		} else if(!r1.equals(r1)) {
			System.err.println("range equality not reflexive");
		} else if(r1.hashCode() != r1.hashCode()) {
			System.err.println("range hashcode not reflexive");
		} else if(!r1.equals(r2)) {
			System.err.println("range " + r1 + " and " + r2 + " not equal");
		} else if(r1.hashCode() != r2.hashCode()) {
			System.err.println("ranges " + r1 + " and " + r2 + " do not have same hash codes");
		} else if(r1.equals(r3)) {
			System.err.println("ranges " + r1 + " and " + r3 + " are equal");
		} else if (r1.hashCode() == r3.hashCode()) {
			System.err.println("range " + r1 + " and " + r3 + " have same hash code");
		} else if(r1.equals(r4)) {
			System.err.println("ranges " + r1 + " and " + r4 + " are equal");
		} else if(r1.hashCode() == r4.hashCode()) {
			System.err.println("range " + r1 + " and " + r4 + " have same hash code");
		} else if(r1.equals(r5)) {
			System.err.println("ranges " + r1 + " and " + r5 + " are equal");
		} else if(r1.hashCode() == r5.hashCode()) {
			System.err.println("range " + r1 + " and " + r5 + " have same hash code");
		} else {
			System.out.println("range comparisons okay");
		}

		db.dispose();
		//cluster.dispose();
		//fdb.stopNetwork();
		System.out.println("Done with test program");
	}

	private static void checkRange(Transaction tr) throws FDBException {
		long version = tr.getReadVersion().get();
		System.out.println("DB version: " + version);
		byte[] val = tr.get("apple4".getBytes()).get();
		System.out.println("Value is " +
				(val != null ? new String(val) : "not present"));

		 AsyncIterable<KeyValue> entryList = tr.getRange(
				KeySelector.firstGreaterOrEqual("apple".getBytes()),
				KeySelector.firstGreaterOrEqual("banana".getBytes()),4);
		List<KeyValue> entries = entryList.asList().get();

		System.out.println("List size is " + entries.size());
		for(int i=0; i < entries.size(); i++) {
			String key = new String(entries.get(i).getKey());
			String value = new String(entries.get(i).getValue());
			System.out.println(" (" + i + ") -> " + key + ", " + value);
		}

		System.out.println("\nAlso:");
		for(KeyValue kv : entryList) {
			String key = new String(kv.getKey());
			String value = new String(kv.getValue());
			System.out.println(" -- " + key + " -> " + value);
		}

	}
}