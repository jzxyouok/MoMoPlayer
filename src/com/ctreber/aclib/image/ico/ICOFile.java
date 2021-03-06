package com.ctreber.aclib.image.ico;

import java.awt.Dimension;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import com.ctreber.aclib.codec.AbstractDecoder;
import com.ctreber.aclib.codec.StreamDecoder;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

/**
 * <p>
 * ICO file with one or more embedded bitmaps representing icons in various
 * resolutions. An ICO file essentially is a format that glues together a couple
 * of bitmaps into a single file.
 * </p>
 * <p>
 * This code uses file format information gleaned from:
 * </p>
 * <p>
 * winicontoppm.c - read a MS Windows .ico file and write portable pixmap(s)
 * </p>
 * <p>
 * Copyright (C) 2000 by Lee Benfield - lee@recoil.org
 * </p>
 * <p>
 * Permission to use, copy, modify, and distribute this software and its
 * documentation for any purpose and without fee is hereby granted, provided
 * that the above copyright notice appear in all copies and that both that
 * copyright notice and this permission notice appear in supporting
 * documentation. This software is provided "as is" without express or implied
 * warranty.
 * </p>
 * <p>
 * Addendum for Java code by Christian Treber:
 * </p>
 * <p>
 * The rules for the Java adaption are the same as stated above for the C
 * version.
 * </p>
 * <p>
 * Notes: All code in 1:10h. Realized stuff is little endian. At 2:30h:
 * Debugged, got stuck on wrong determination of row length in 8 BPP images. Got
 * black images until I set the alpha channel... Put some more effort into
 * supporting mask; all in all I would say this took me 4:00h. Had to add
 * another 4:00h for research into 24 and 32 BPP.
 * </p>
 * @author &copy; Christian Treber, ct@ctreber.com
 */
public class ICOFile implements Comparable<Object> {

    /** Source file name. */
    private String _fileName;

    /** Unspecified purpose. */
    private int _reserved;

    private int _type;

    /** Number of contained images. */
    private int _imageCount;

    private final List<BitmapDescriptor> _descriptors = new ArrayList<BitmapDescriptor>();

    /**
     * Create ICOFile object from an ICO file. Use {@link #getDescriptors()}to
     * access the icon(s). Yes, ICO files might contain more than one icon).
     * @param pFileName
     *            Name of the file to read.
     * @throws IOException
     */
    public ICOFile(final String pFileName) throws IOException {
        this(pFileName, new StreamDecoder(new FileInputStream(pFileName)));
    }

    /**
     * Create ICO file from an input stream.
     * @param pInput
     * @throws IOException
     */
    public ICOFile(final InputStream pInput) throws IOException {
        this("[from stream]", new StreamDecoder(pInput));
    }

    /**
     * Create ICO file from an URL.
     * @param pURL
     * @throws IOException
     */
    public ICOFile(final URL pURL) throws IOException {
        this(pURL.toString(), new StreamDecoder(pURL.openStream()));
    }

    /**
     * Create ICOFile from a byte array.
     * @param pBuffer
     * @throws IOException
     */
    public ICOFile(final byte[] pBuffer) throws IOException {
        this("[from buffer]", new StreamDecoder(new ByteArrayInputStream(
                pBuffer)));
    }

    /**
     * Create ICO file.
     * @param pFileName
     *            Just serves as information for toString() output; input is
     *            obtained through pFileDecoder.
     * @param pFileDecoder
     *            Decoder to read from.
     * @throws IOException
     *             If anything goes wrong with reading from the decoder.
     */
    // @PMD:REVIEWED:CallSuperInConstructor: by Chris on 06.03.06 10:32
    public ICOFile(final String pFileName, final AbstractDecoder pFileDecoder)
            throws IOException {
        _fileName = pFileName;
        read(pFileDecoder);
    }

    public int compareTo(final Object pOther) {
        if (!(pOther instanceof ICOFile)) {
            throw new IllegalArgumentException("Can't compare to "
                    + pOther.getClass());
        }

        return ((ICOFile) pOther).getFileName().compareTo(this.getFileName());
    }

    public String toString() {
        final StringBuffer lSB = new StringBuffer(100);

        lSB.append(_fileName + ", type: " + _type + ", image count: "
                + _imageCount);
        // Iterator lIt = _entries.iterator();
        // while (lIt.hasNext())
        // {
        // BitmapDescriptor lBitmapDescriptor = (BitmapDescriptor) lIt.next();
        // lSB.append(lBitmapDescriptor);
        // }
        return lSB.toString();
    }

    /**
     * Read the ICO file. The file consists of a header (with type and image
     * count), a list of image entries (describing image properties and offsets
     * into the ICO file), and the image data itself. The image data for each
     * image consists of a header (describing some more image properties) and
     * the bitmap.
     * @param pDec
     *            Decoder to read from.
     * @throws IOException
     */
    private void read(final AbstractDecoder pDec) throws IOException {
        pDec.setEndianess(StreamDecoder.LITTLE_ENDIAN);

        readHeader(pDec);
        final BitmapDescriptor[] lDescriptors = readDescriptors(pDec);
        fillDescriptors(pDec, lDescriptors);
        pDec.close();
    }

    /**
     * @param pDec The decoder.
     * @throws IOException
     */
    private void readHeader(final AbstractDecoder pDec) throws IOException {
        _reserved = pDec.readUInt2();
        _type = pDec.readUInt2();
        _imageCount = pDec.readUInt2();

        if (_type != 1) {
            throw new IllegalArgumentException("Unknown ICO type " + _type);
        }

        if (_imageCount == 0) {
            // Yes, I found some ICO files say "0" images, but they contain one.
            _imageCount = 1;
        }
    }

    /**
     * @param pDec The decoder.
     * @throws IOException
     */
    private void fillDescriptors(final AbstractDecoder pDec,
            final BitmapDescriptor[] pDescriptors) throws IOException {
        for (int lDescriptorNo = 0; lDescriptorNo < pDescriptors.length; lDescriptorNo++) {
            final BitmapDescriptor lDescriptor = pDescriptors[lDescriptorNo];
            fillDescriptor(pDec, lDescriptor);
            _descriptors.add(lDescriptor);
        }
    }

    /**
     * @param pDec The decoder.
     * @param pDescriptor
     * @throws IOException
     */
    private void fillDescriptor(final AbstractDecoder pDec,
            final BitmapDescriptor pDescriptor) throws IOException {
        if (pDec.getPos() != pDescriptor.getOffset()) {
            pDec.seek(pDescriptor.getOffset());
        }

        pDescriptor.setHeader(new BitmapHeader(pDec));
        pDescriptor.setBitmap(readBitmap(pDec, pDescriptor));
        doSomeChecks(pDescriptor);
    }

    /**
     * @param pDec The decoder.
     * @return
     * @throws IOException
     */
    private BitmapDescriptor[] readDescriptors(final AbstractDecoder pDec)
            throws IOException {
        final BitmapDescriptor[] lEntries = new BitmapDescriptor[_imageCount];
        for (int lImageNo = 0; lImageNo < _imageCount; lImageNo++) {
            lEntries[lImageNo] = readDescriptor(pDec);
        }
        return lEntries;
    }

    /**
     * @param pDec The decoder.
     * @return
     * @throws IOException
     */
    private BitmapDescriptor readDescriptor(final AbstractDecoder pDec)
            throws IOException {
        return new BitmapDescriptor(pDec);
    }

    /**
     * Perform some sanity checks.
     */
    private void doSomeChecks(final BitmapDescriptor pDescriptor) {
        if (pDescriptor.getHeader().getWidth() * 2 != pDescriptor.getHeader()
                .getHeight()) {
            System.out.println(this
                    + ": In header, height is not twice the width");
        }
    }

    /**
     * @param pDec The decoder.
     * @return Bitmap, type depends on BPP
     * @throws IOException
     */
    private AbstractBitmap readBitmap(final AbstractDecoder pDec,
            final BitmapDescriptor pDescriptor) throws IOException {
        final int lBitsPerPixel = pDescriptor.getHeader().getBPP();

        AbstractBitmap lBitmap = null;
        switch (lBitsPerPixel) {
        // Palette style
        case 1:
            lBitmap = new BitmapIndexed1BPP(pDescriptor);
            break;
        case 4:
            lBitmap = new BitmapIndexed4BPP(pDescriptor);
            break;
        case 8:
            lBitmap = new BitmapIndexed8BPP(pDescriptor);
            break;

        // RGB style
        case 16:
            return null;
        case 24:
            lBitmap = new BitmapRGB24BPP(pDescriptor);
            break;
        case 32:
            lBitmap = new BitmapRGB32BPP(pDescriptor);
            break;

        default:
            throw new IllegalArgumentException("Unsupported bit count "
                    + lBitsPerPixel);
        }
        lBitmap.read(pDec);

        return lBitmap;
    }

    /**
     * Get all contained images (comfort method).
     * @return Images (type Image).
     */
    public List<BufferedImage> getImages() {
        final List<BufferedImage> lImages = new ArrayList<BufferedImage>();

        final Iterator<BitmapDescriptor> lItDesc = getDescriptors().iterator();
        while (lItDesc.hasNext()) {
            final BitmapDescriptor lDesc = (BitmapDescriptor) lItDesc.next();
            lImages.add(lDesc.getBitmap().createImageRGB());
        }

        return lImages;
    }

    /**
     * Get the list of BitmapDescriptors contained in the ICO file.
     * @return List of {@link BitmapDescriptor}in same order as in the ICO file
     *         (use methods on ICOEntry to get the actual images).
     */
    public List<BitmapDescriptor> getDescriptors() {
        return _descriptors;
    }

    /**
     * Get the speicified BitmapDescriptor.
     * @param pDescriptorNo
     *            Number of the descriptor to get.
     * @return BitmapDescriptor.
     */
    public BitmapDescriptor getDescriptor(final int pDescriptorNo) {
        return (BitmapDescriptor) _descriptors.get(pDescriptorNo);
    }

    /**
     * Get the image type.
     * @return The image type (any ideas what that is?).
     */
    public int getType() {
        return _type;
    }

    /**
     * Get the number of contained images.
     * @return Number of contained images.
     */
    public int getImageCount() {
        return _imageCount;
    }

    /**
     * @return Source file name.
     */
    public String getFileName() {
        return _fileName;
    }

    /**
     * @return Returns the "reserved" value.
     */
    public int getReserved() {
        return _reserved;
    }
    public static void main(String[] args)throws Exception {
        ICOFile file=new ICOFile("E:/build/client.ico");
        System.out.println(file.getImageCount());
        final BufferedImage bi=(BufferedImage)file.getImages().get(file.getImageCount()-1);
        System.out.println(bi.getWidth());
        System.out.println(bi.getHeight());
        JOptionPane.showMessageDialog(null, new JPanel(){
            /**
			 * long serialVersionUID
			 */
			private static final long serialVersionUID = 1L;

			public void paintComponent(Graphics g){
                g.drawImage(bi, 0, 0, this);
            }

            @Override
            public Dimension getPreferredSize() {
                return new Dimension(200,200);
            }

        });
    }
}
