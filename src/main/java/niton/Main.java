package niton;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifSubIFDDirectory;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class Main {
	public static void main(String[] args) throws IOException, ImageProcessingException {
		System.out.println("+----------------------+");
		System.out.println("|continutiy-check 0.2.7|");
		System.out.println("+----------------------+");
		Scanner s = new Scanner(System.in);
		System.out.print("Folder to scan > ");
		String path = s.nextLine();
		String os= System.getProperty("os.name").toLowerCase();
		System.out.println("OS : "+os);
		if(path.contains(":") && (os.contains("unix") || os.contains("linux") || os.contains("buntu"))) {
			path = path.replace(path.subSequence(0, 3), "/mnt/" + Character.toLowerCase(path.charAt(0))+"/");
			System.out.println("Translated windows path to wsl : "+path);
		}
		File folder = new File(path);
		System.out.print("Visit subfolders (y/n) >");
		boolean subs = s.nextLine().equalsIgnoreCase("y");
		System.out.print("Use indentation (y/n) >");
		boolean indent = s.nextLine().equalsIgnoreCase("y");
		System.out.print("Short output (y/n) >");
		boolean shorter = s.nextLine().equalsIgnoreCase("y");

		File[] folders;
		if (subs) {
			folders = folder.listFiles(f -> !f.isFile());
		} else {
			folders = new File[]{folder};
		}
		Arrays.parallelSort(folders, Comparator.comparing(File::getAbsolutePath));
		for (File file : folders) {
			System.out.print("\nAnalysis > " + file + (shorter?"":"\n"));
			File[] files = file.listFiles(
					f -> (f.isFile() &&
							(f.getAbsolutePath()
							  .toLowerCase()
							  .endsWith(".jpg") ||
									f.getAbsolutePath()
									 .toLowerCase()
									 .endsWith(".jpeg") ||
									f.getAbsolutePath()
									 .toLowerCase()
									 .endsWith(".png")))
			);
			System.out.print((shorter?"\t":"")+"Found " + files.length + " images"+ (shorter?"":"\n"));
			SortedMap<LocalDate, List<File>> map = new TreeMap<>();
			System.out.print("Scan : ");
			for (int i = 0; i < files.length; i++) {
				File f = files[i];
				System.out.print("\rScan : " + f.getName());
				System.out.print("\r");
				try {
					Metadata metadata = ImageMetadataReader.readMetadata(f);
					ExifSubIFDDirectory directory = metadata.getFirstDirectoryOfType(
							ExifSubIFDDirectory.class);
					long timeInMs = directory.getDate(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL)
					                         .getTime();
					LocalDate key = Instant.ofEpochMilli(timeInMs)
					                       .atZone(ZoneId.systemDefault())
					                       .toLocalDate();
					if (!map.containsKey(key)) {
						map.put(key, new ArrayList<>());
					}
					map.get(key).add(f);
				} catch (Exception e) {
					if(!shorter)
						System.out.println("Error on this file(" + f + ") : " + e);
				}
			}
			DateTimeFormatter form = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT);
			for (Map.Entry<LocalDate, List<File>> day : map.entrySet()) {
				try {
					day.getValue().sort(Comparator.comparingLong(Main::getFileCreationEpoch));
					System.out.print("\n"+form.format(day.getKey()) + " : "+ (shorter?"":"\n"));
					System.out.println((indent ? "\t" : "") + "Files : " + day.getValue().size());
					File first = day.getValue().get(0);
					System.out.print((indent ? "\t" : "") + "First : " + first.getName()+ (shorter?"":"\n"));
					File last = day.getValue().get(day.getValue().size() - 1);
					System.out.print((indent ? "\t" : "") + "Last  : " + last.getName()+ (shorter?"":"\n"));
					int start = parse(first), end = parse(last);
					System.out.println((indent ? "\t" : "") + "Missing: " + (((end - start) + 1) - day
							.getValue()
							.size()));
					List<Integer> numbs = day.getValue()
					                        .stream()
					                        .map(Main::parse)
					                        .collect(Collectors.toList());
					if(!shorter)
						if (indent) {
							for (int i = start; i < end; i++) {
								if (!numbs.contains(i)) {
									System.out.println("\t\t> " + i);
								}
							}
						} else {
							System.out.println(IntStream.range(start,end).filter((i)->!numbs.contains(i)).mapToObj(String::valueOf).collect(Collectors.joining(", ")));
						}
				} catch (Exception e) {
					if(!shorter)
						System.out.println("Failure interpreting a file from " + day.getKey());
				}
			}
		}

	}

	public static long getFileCreationEpoch(File file) {
		try {
			Metadata            metadata  = ImageMetadataReader.readMetadata(file);
			ExifSubIFDDirectory directory = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
			Date                date      = directory.getDate(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL);
			return date.getTime();
		} catch (IOException | ImageProcessingException e) {
			throw new RuntimeException(file.getAbsolutePath(), e);
		}
	}

	public static int parse(File f) {
		int firstPoint = f.getName().indexOf(".");
		return Integer.parseInt(f.getName().substring(firstPoint - 4, firstPoint));
	}
}
