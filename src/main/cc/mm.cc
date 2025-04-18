// See LICENSE for license details.

#include "mm.h"
#include "elf.h"
#include <iostream>
#include <fstream>
#include <cstdlib>
#include <cstring>
#include <string>
#include <cassert>
#include <sys/stat.h>
#include <fcntl.h>
#include <unistd.h>
#include <sys/mman.h>

mm_magic_t::mm_magic_t(size_t size, size_t word_size):
  data(new char[size]),
  size(size),
  word_size(word_size), 
  store_inflight(false)
{
  dummy_data.resize(word_size);
}

mm_magic_t::~mm_magic_t()
{
  delete [] data;
}

void mm_magic_t::write(uint64_t addr, char *data) {
  addr %= this->size;

  char* base = this->data + addr;
  memcpy(base, data, word_size);
}

void mm_magic_t::write(uint64_t addr, char *data, uint64_t strb, uint64_t size)
{
  strb &= ((1L << size) - 1) << (addr % word_size);
  addr %= this->size;

  char *base = this->data + addr;
  for (int i = 0; i < word_size; i++) {
    if (strb & 1) base[i] = data[i];
    strb >>= 1;
  }
}

std::vector<char> mm_magic_t::read(uint64_t addr)
{
  addr %= this->size;

  char *base = this->data + addr;
  return std::vector<char>(base, base + word_size);
}

void mm_magic_t::tick(
  bool reset,
  bool ar_valid,
  uint64_t ar_addr,
  uint64_t ar_id,
  uint64_t ar_size,
  uint64_t ar_len,

  bool aw_valid,
  uint64_t aw_addr,
  uint64_t aw_id,
  uint64_t aw_size,
  uint64_t aw_len,

  bool w_valid,
  uint64_t w_strb,
  void *w_data,
  bool w_last,

  bool r_ready,
  bool b_ready)
{
  bool ar_fire = !reset && ar_valid && ar_ready();
  bool aw_fire = !reset && aw_valid && aw_ready();
  bool w_fire = !reset && w_valid && w_ready();
  bool r_fire = !reset && r_valid() && r_ready;
  bool b_fire = !reset && b_valid() && b_ready;

  if (ar_fire) {
    uint64_t start_addr = (ar_addr / word_size) * word_size;
    for (size_t i = 0; i <= ar_len; i++) {
      auto dat = read(start_addr + i * word_size);
      rresp.push(mm_rresp_t(ar_id, dat, i == ar_len));
    }
  }

  if (aw_fire) {
    store_addr = aw_addr;
    store_id = aw_id;
    store_count = aw_len + 1;
    store_size = 1 << aw_size;
    store_inflight = true;
  }

  if (w_fire) {
    write(store_addr, (char*)w_data, w_strb, store_size);
    store_addr += store_size;
    store_count--;

    if (store_count == 0) {
      store_inflight = false;
      bresp.push(store_id);
      assert(w_last);
    }
  }

  if (b_fire)
    bresp.pop();

  if (r_fire)
    rresp.pop();

  cycle++;

  if (reset) {
    while (!bresp.empty()) bresp.pop();
    while (!rresp.empty()) rresp.pop();
    cycle = 0;
  }
}

void load_mem(long long startAddr ,char* mem, const char* fn)
{
  long long start = startAddr;
  std::ifstream in(fn);
  if (!in)
  {
    std::cerr << "could not open " << fn << std::endl;
    exit(EXIT_FAILURE);
  }

  std::string line;
  while (std::getline(in, line))
  {
    #define parse_nibble(c) ((c) >= 'a' ? (c)-'a'+10 : (c)-'0')
    for (int i = 0, j = 0; i <= line.length()-2; i += 2, j++) {
      mem[start + j] = (parse_nibble(line[i]) << 4) | parse_nibble(line[i+1]);
    }
    start += line.length()/2;
  }
}

void load_elf(const char * filePath,char *memory){
  int fd = open(filePath,O_RDONLY);
  struct stat fs;
  fstat(fd,&fs);
  void *elf = mmap(NULL,fs.st_size,PROT_READ,MAP_PRIVATE,fd,0);
  if(elf == MAP_FAILED) std::cout << "load elf file" << std::endl;
  copyfile((struct elfhdr*)elf,memory);
  munmap(elf,fs.st_size);
  close(fd);
}

void copyfile(struct elfhdr *elf,char *memory)
{
	struct proghdr *ph  = (struct proghdr*)((char*)elf + (elf -> phoff));
	struct proghdr *eph = (struct proghdr*)(ph  + elf -> phnum);
	for (; ph < eph; ++ph) {
		char *dst = memory + ph -> paddr;
		char *pa  = (char*)elf;
		char *src = pa + ph -> off;
		memmove(dst, src, ph -> filesz);
		if (ph -> memsz > ph -> filesz) {
			memset(dst + ph -> filesz, 0, ph->memsz - ph->filesz); 
		}
	}
}
