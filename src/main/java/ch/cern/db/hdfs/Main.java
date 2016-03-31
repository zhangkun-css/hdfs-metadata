/**
 * Copyright (C) 2016, CERN
 * This software is distributed under the terms of the GNU General Public
 * Licence version 3 (GPL Version 3), copied verbatim in the file "LICENSE".
 * In applying this license, CERN does not waive the privileges and immunities
 * granted to it by virtue of its status as Intergovernmental Organization
 * or submit itself to any jurisdiction.
 */
package ch.cern.db.hdfs;

import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.BlockLocation;
import org.apache.hadoop.fs.BlockStorageLocation;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.VolumeId;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.cern.db.util.SUtils;
import ch.cern.db.util.SUtils.Color;

public class Main extends Configured implements Tool {

	private static final Logger LOG = LoggerFactory.getLogger(Main.class);

	private static void printBlockMetadata(List<BlockLocation> blockLocations, String[] dataDirs,
			int limitPrintedBlocks) throws IOException {
		System.out.println();
		System.out.println(" === Metadata of blocks and replicass ===");
		System.out.println();
		
		if(limitPrintedBlocks < 1){
			LOG.warn("Not showing blocks because limit has been configured to 0");
			return;
		}
		
		int j = 0;
		for (BlockLocation blockLocation : blockLocations) {
			System.out.println("Block (" + j + ") info:");
			
			printBlockMetadata(blockLocation, dataDirs);
			
			j++;
			
			System.out.println();
			
			if(j >= limitPrintedBlocks){
				LOG.warn("Not showing more blocks because limit has been reached");
				break;
			}
		}
		
		System.out.println();
	}

	private static void printBlockMetadata(BlockLocation blockLocation, String[] dataDirs) throws IOException {

		System.out.println("	Offset: " + blockLocation.getOffset());
		System.out.println("	Length: " + blockLocation.getLength());

		String[] cachedHosts = blockLocation.getCachedHosts();
		if (cachedHosts.length == 0) {
			System.out.println("	No cached hosts");
		}

		System.out.println("	Hosts:");
		VolumeId[] volumeIds = blockLocation instanceof BlockStorageLocation ?
				(((BlockStorageLocation) blockLocation).getVolumeIds()) : null;
		String[] hosts = blockLocation.getHosts();
		String[] names = blockLocation.getNames();
		String[] topologyPaths = blockLocation.getTopologyPaths();
		for (int i = 0; i < topologyPaths.length; i++) {
			int diskId = volumeIds != null ? DistributedFileSystemMetadata.getDiskId(volumeIds[i]) : -1;
			
			System.out.println("		Replica (" + i + "):");
			System.out.println("			Host: " + hosts[i]);
			
			if(diskId == -1)
				System.out.println("			DiskId: unknown");
			else if(dataDirs != null && diskId < dataDirs.length)
				System.out.println("			Location: " + dataDirs[diskId] + " (DiskId: " + diskId + ")");
			else
				System.out.println("			DiskId: " + diskId);
			
			System.out.println("			Name: " + names[i]);
			System.out.println("			TopologyPaths: " + topologyPaths[i]);
		}

		if (cachedHosts.length > 0) {
			System.out.println("	Cached hosts:");
			for (String cachedHost : cachedHosts) {
				System.out.println("		Host: " + cachedHost);
			}
		}
	}

	static void printNodeDisksDistribution(HashMap<String, HashMap<Integer, Integer>> hosts_diskIds,
			Integer numDisks) {
		
		System.out.println();
		System.out.println(" === Distribution along nodes and disks ===");
		System.out.println();
		
		numDisks = Math.max(getMaxDiskId(hosts_diskIds) + 1, numDisks);
		
		System.out.print(SUtils.adjustLength("DiskId", 25));
		if(numDisks >= 10){
			for (int i = 0; i < numDisks; i++) {
				System.out.print(SUtils.adjustLength(Integer.toString(i / 10), 2));
			}
			System.out.println();
			System.out.print(SUtils.adjustLength("", 25));
		}
		for (int i = 0; i < numDisks; i++) {
			System.out.print(SUtils.adjustLength(Integer.toString(i % 10), 2));
		}
		System.out.print(SUtils.adjustLength("Unknown", 10));
		System.out.print(SUtils.adjustLength("Count", 10));
		System.out.println(SUtils.adjustLength("Average", 10));
		System.out.println("Host");
		for (Entry<String, HashMap<Integer, Integer>> host_diskIds : hosts_diskIds.entrySet()) {
			System.out.print(SUtils.adjustLength(host_diskIds.getKey(), 25));
			
			HashMap<Integer, Integer> diskIds_count = host_diskIds.getValue();
			
			float sum = 0;
			for (int i = 0; i < numDisks; i++){
				Integer count = diskIds_count.get(i);
				
				if(count != null)
					sum += diskIds_count.get(i);
			}
			float avg = sum / numDisks;
			
			float low = (float) (avg - 0.2 * avg);
			if(avg - low < 2)
				low = avg - 2;			
			float high = (float) (avg + 0.2 * avg);
			if(high - avg < 2)
				high = avg + 2;
			
			for (int i = 0; i < numDisks; i++) {
				Integer count = diskIds_count.get(i);
				
				if(count == null)
					System.out.print(SUtils.color(Color.R, SUtils.adjustLength("0", 2)));
				else if(count < low)
					System.out.print(SUtils.color(Color.Y, SUtils.adjustLength("-", 2)));
				else if(count > high)
					System.out.print(SUtils.color(Color.Y, SUtils.adjustLength("+", 2)));
				else
					System.out.print(SUtils.color(Color.G, SUtils.adjustLength("=", 2)));
			}
			
			Integer count_unk = diskIds_count.get(-1);
			if(count_unk == null)
				System.out.print(SUtils.color(Color.G, SUtils.adjustLength("0", 10)));
			else
				System.out.print(SUtils.color(Color.R, SUtils.adjustLength(count_unk+"", 10)));
			
			if(diskIds_count.containsKey(-1))
				sum += diskIds_count.get(-1);
			
			System.out.print(SUtils.adjustLength((int)sum+"", 10));
			System.out.println((int) avg);
		}
		
		System.out.println();
		System.out.println("Leyend");
		System.out.println("  " + SUtils.color(Color.R,  "0") + ": no blocks in this disk");
		System.out.println("  " + SUtils.color(Color.Y,  "+") + ": #blocks is more than 20% of the avergae of blocks per disk of this host");
		System.out.println("  " + SUtils.color(Color.G, "=") + ": #blocks is aproximatilly the avergae of blocks per disk of this host");
		System.out.println("  " + SUtils.color(Color.Y,  "-") + ": #blocks is less than 20% of the avergae of blocks per disk of this host");
	}

	private static int getMaxDiskId(HashMap<String, HashMap<Integer, Integer>> hosts_diskIds) {
		
		Integer maxDiskId = -1;
		
		for (HashMap<Integer, Integer> host_diskIds : hosts_diskIds.values())
			for (Integer diskId : host_diskIds.keySet())
				if(diskId > maxDiskId)
					maxDiskId = diskId;
		
		return maxDiskId;
	}

	private static void printFileStatus(FileStatus status) {
		System.out.println();
		System.out.println("Showing metadata for: " + status.getPath());
		System.out.println("	isDirectory: " + status.isDirectory());
		System.out.println("	isFile: " + status.isFile());
		System.out.println("	isSymlink: " + status.isSymlink());
		System.out.println("	encrypted: " + status.isEncrypted());
		System.out.println("	length: " + status.getLen());
		System.out.println("	replication: " + status.getReplication());
		System.out.println("	blocksize: " + status.getBlockSize());
		System.out.println("	modification_time: " + new Date(status.getModificationTime()));
		System.out.println("	access_time: " + new Date(status.getAccessTime()));
		System.out.println("	owner: " + status.getOwner());
		System.out.println("	group: " + status.getGroup());
		System.out.println("	permission: " + status.getPermission());
		System.out.println();
	}

	public static void main(String[] args) throws Exception {
		int res = ToolRunner.run(new Configuration(), new Main(), args);
        System.exit(res);
	}
	
	@Override
	public int run(String[] args) throws Exception {

		if (args.length == 0 || args.length > 2) {
			System.err.println("You need to specify a path as first argument.");
			System.exit(1);
		}
		
		int limitPrintedBlocks = 20;
		if(args.length == 2){
			limitPrintedBlocks = Integer.parseInt(args[1]);
		}
		
		Path path = new Path(args[0]);
		
		@SuppressWarnings("resource")
		DistributedFileSystemMetadata fsm = new DistributedFileSystemMetadata(getConf());
		
		printFileStatus(fsm.getFileStatus(path));
		
		String[] dataDirs = fsm.getDataDirs();
		if(dataDirs != null){
			System.out.println();
			System.out.println("Data directories and disk ids");
			for (int i = 0; i < dataDirs.length; i++) {
				System.out.println("  DiskId: " + i + "  Directory: " + dataDirs[i]);
			}
		}
		System.out.println();
		
		List<BlockLocation> blockLocations = fsm.getBlockLocations(path);
		
		HashMap<String, HashMap<Integer, Integer>> hosts_diskIds = 
				DistributedFileSystemMetadata.computeHostsDiskIdsCount(blockLocations);
			
		//Fill with not existing data nodes
		String[] dataNodes = fsm.getDataNodes();
		for (String name : dataNodes)
			if(!hosts_diskIds.containsKey(name))
				hosts_diskIds.put(name, new HashMap<Integer, Integer>());
			
		printNodeDisksDistribution(hosts_diskIds, dataDirs != null ? dataDirs.length : -1);
			
		printBlockMetadata(blockLocations, dataDirs, limitPrintedBlocks);
	
		return 0;
	}

}
