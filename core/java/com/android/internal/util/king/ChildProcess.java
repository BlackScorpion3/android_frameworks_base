package com.android.internal.util.king;

import android.os.Environment;
import android.util.Log;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;

import static java.lang.System.nanoTime;

public class ChildProcess {
    private String TAG = getClass().getSimpleName();

    private static final int PIPE_SIZE = 1024;

    private class ChildReader extends Thread {
        InputStream mStream;
        StringBuffer mBuffer;
        ChildReader(InputStream is, StringBuffer buf) {
            mStream = is;
            mBuffer = buf;
        }
        public void run() {
            byte[] buf = new byte[PIPE_SIZE];
            try {
                int len;
                while ((len = mStream.read(buf)) != -1) {
                    String s = new String(buf, 0, len);
                    mBuffer.append(s);
                }
            }
            catch (IOException e) {
                // Ignore
            }
            try {
                mStream.close();
            }
            catch (IOException e) {
                // Ignore
            }
        }
    }
    private class ChildWriter extends Thread {
        OutputStream mStream;
        String mBuffer;
        ChildWriter(OutputStream os, String buf) {
            mStream = os;
            mBuffer = buf;
        }
        public void run() {
            int off = 0;
            byte[] buf = mBuffer.getBytes();
            try {
                while (off < buf.length) {
                    int len = Math.min(PIPE_SIZE, buf.length - off);
                    mStream.write(buf, off, len);
                    off += len;
                }
            }
            catch (IOException e) {
                // Ignore
            }
            try {
                mStream.close();
            }
            catch (IOException e) {
                // Ignore
            }
        }
    }

    private long mStartTime;
    private Process mChildProc;
    private ChildWriter mChildStdinWriter;
    private ChildReader mChildStdoutReader;
    private ChildReader mChildStderrReader;
    private StringBuffer mChildStdout;
    private StringBuffer mChildStderr;
    private int mExitValue;
    private long mEndTime;

    public ChildProcess(String[] cmdarray, String childStdin) {
        mStartTime = nanoTime();
        try {
            mChildProc = Runtime.getRuntime().exec(cmdarray);
            if (childStdin != null) {
                mChildStdinWriter = new ChildWriter(mChildProc.getOutputStream(), childStdin);
                mChildStdinWriter.start();
            }
            mChildStdout = new StringBuffer();
            mChildStdoutReader = new ChildReader(mChildProc.getInputStream(), mChildStdout);
            mChildStdoutReader.start();
            mChildStderr = new StringBuffer();
            mChildStderrReader = new ChildReader(mChildProc.getErrorStream(), mChildStderr);
            mChildStderrReader.start();
        }
        catch (IOException e) {
            // XXX: log
        }
    }

    public boolean isFinished() {
        boolean finished = true;
        if (mChildProc != null) {
            try {
                mChildProc.exitValue();
            }
            catch (IllegalStateException e) {
                finished = false;
            }
        }
        return finished;
    }

    public int waitFinished() {
        while (mChildProc != null) {
            try {
                mExitValue = mChildProc.waitFor();
                mEndTime = nanoTime();
                mChildProc = null;
                mChildStderrReader.join();
                mChildStderrReader = null;
                mChildStdoutReader.join();
                mChildStdoutReader = null;
                if (mChildStdinWriter != null) {
                    mChildStdinWriter.join();
                    mChildStdinWriter = null;
                }
            }
            catch (InterruptedException e) {
                // Ignore
            }
        }
        return mExitValue;
    }

    public CommandResult getResult() {
        if (!isFinished()) {
            throw new IllegalThreadStateException("Child process running");
        }
        return new CommandResult(
                mStartTime,
                mExitValue,
                mChildStdout.toString(),
                mChildStderr.toString(),
                mEndTime);
    }
}
